package org.checkerframework.checker.modifiability;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.checker.modifiability.qual.ThrowsUOE;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/**
 * Base visitor for the modifiability sub-checkers (Grow, Shrink, Replace).
 *
 * <p>This class contains logic shared across all three sub-checkers:
 *
 * <ul>
 *   <li>Reporting errors for invocations of methods annotated with {@link ThrowsUOE}.
 *   <li>Suppressing the "constructor result must be TOP" check, since collection constructors may
 *       legitimately produce {@code @Modifiable}.
 * </ul>
 */
public class ModifiabilityVisitor extends BaseTypeVisitor<BaseAnnotatedTypeFactory> {

  /** The erased {@code java.util.Collection} type. */
  private final TypeMirror collectionErasure;

  /** The erased {@code java.util.Map} type. */
  private final TypeMirror mapErasure;

  /** The erased {@code java.util.Iterator} type. */
  private final TypeMirror iteratorErasure;

  /**
   * Create a ModifiabilityVisitor.
   *
   * @param checker the checker that uses this visitor
   */
  public ModifiabilityVisitor(BaseTypeChecker checker) {
    super(checker);
    this.collectionErasure =
        atypeFactory.types.erasure(
            atypeFactory.getElementUtils().getTypeElement("java.util.Collection").asType());
    this.mapErasure =
        atypeFactory.types.erasure(
            atypeFactory.getElementUtils().getTypeElement("java.util.Map").asType());
    this.iteratorErasure =
        atypeFactory.types.erasure(
            atypeFactory.getElementUtils().getTypeElement("java.util.Iterator").asType());
  }

  @Override
  public void processClassTree(ClassTree classTree) {
    super.processClassTree(classTree);
    if (shouldCheckCustomModifiabilityAnnotation()) {
      TypeElement typeElement = TreeUtils.elementFromDeclaration(classTree);
      if (typeElement != null && isCollectionFromSourceCode(typeElement)) {
        checker.reportWarning(
            classTree, "modifiability.annotation.unverified", typeElement.getQualifiedName());
      }
    }
  }

  @Override
  public Void visitAnnotation(AnnotationTree tree, Void p) {
    if (shouldCheckUnmodifiableParamLocation() && isUnmodifiableParamAnnotation(tree)) {
      if (!isWithinAllowedUnmodifiableParamLocation()) {
        checker.reportError(tree, "unmodparam.location");
      }
    }
    return super.visitAnnotation(tree, p);
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
    // Methods annotated @ThrowsUOE always throw UnsupportedOperationException when called.
    // When running as part of the aggregate ModifiabilityChecker, only the GrowChecker
    // reports this error (shouldCheckThrowsUOE() returns false in the other two) to avoid
    // tripling the diagnostic. When running a sub-checker standalone, each reports it.
    if (shouldCheckThrowsUOE()) {
      ExecutableElement method = TreeUtils.elementFromUse(node);
      if (atypeFactory.getDeclAnnotation(method, ThrowsUOE.class) != null) {
        checker.reportError(node, "usage.throws.uoe", method.getSimpleName());
      }
    }
    return super.visitMethodInvocation(node, p);
  }

  /**
   * Returns true if this checker should report {@code @ThrowsUOE} errors.
   *
   * <p>The default is {@code true}. {@link
   * org.checkerframework.checker.modifiability.shrink.ShrinkVisitor} and {@link
   * org.checkerframework.checker.modifiability.replace.ReplaceVisitor} override this to return
   * {@code false} when running under the aggregate {@link ModifiabilityChecker}, so that each
   * {@code @ThrowsUOE} call site produces exactly one error rather than three.
   *
   * @return true if this visitor should report {@code @ThrowsUOE} violations
   */
  protected boolean shouldCheckThrowsUOE() {
    return true;
  }

  /**
   * Returns true if this checker should report {@code @UnmodifiableParam} location errors.
   *
   * <p>The default is {@code true}. Shrink and Replace override this to avoid tripling diagnostics
   * when running under the aggregate {@link ModifiabilityChecker}.
   *
   * @return true if this visitor should report {@code @UnmodifiableParam} location errors
   */
  protected boolean shouldCheckUnmodifiableParamLocation() {
    return true;
  }

  /**
   * Returns true if this checker should report warnings for source-defined Collection, Map, and
   * Iterator subtypes whose modifiability annotations are trusted but not verified.
   *
   * <p>The default is {@code true}. Shrink and Replace override this to avoid tripling diagnostics
   * when running under the aggregate {@link ModifiabilityChecker}.
   *
   * @return true if this visitor should report custom modifiability annotation warnings
   */
  protected boolean shouldCheckCustomModifiabilityAnnotation() {
    return true;
  }

  /**
   * Returns true if {@code tree} is an {@link UnmodifiableParam} annotation.
   *
   * @param tree an annotation tree
   * @return true if {@code tree} is an {@code @UnmodifiableParam} annotation
   */
  private boolean isUnmodifiableParamAnnotation(AnnotationTree tree) {
    String annotationName = tree.getAnnotationType().toString();
    // Quick check to avoid expensive annotation resolution for most annotations.
    if (!annotationName.equals("UnmodifiableParam")
        && !annotationName.endsWith(".UnmodifiableParam")) {
      return false;
    }

    AnnotationMirror annotation = TreeUtils.annotationFromAnnotationTree(tree);
    return annotation != null && atypeFactory.areSameByClass(annotation, UnmodifiableParam.class);
  }

  /**
   * Returns true if the current annotation path is inside a method/constructor parameter type or
   * explicit receiver parameter type.
   *
   * @return true if {@code @UnmodifiableParam} is allowed at the current location
   */
  @SuppressWarnings("interning:not.interned") // AST node comparison
  private boolean isWithinAllowedUnmodifiableParamLocation() {
    // Find the declaration that contains the annotation, if any.
    TreePath path = getCurrentPath();
    TreePath variablePath = null;
    while (path != null) {
      if (path.getLeaf() instanceof VariableTree) {
        variablePath = path;
        break;
      }
      path = path.getParentPath();
    }

    if (variablePath == null || variablePath.getParentPath() == null) {
      return false;
    }

    // Ordinary and receiver parameters are represented as VariableTrees under a MethodTree.
    Tree parent = variablePath.getParentPath().getLeaf();
    if (!(parent instanceof MethodTree methodTree)) {
      return false;
    }

    // Allow nested annotations anywhere within an allowed parameter's type.
    VariableTree variable = (VariableTree) variablePath.getLeaf();
    return methodTree.getParameters().contains(variable)
        || methodTree.getReceiverParameter() == variable;
  }

  // Suppresses the framework's "constructor result must be TOP" check.
  // Collection constructors (e.g., new ArrayList()) legitimately produce @Modifiable, which is a
  // subtype of the top type @MaybeModifiable. This suppression is sound: constructors are
  // typed by their stubs or by defaults inferred from the class's annotations, so they cannot
  // silently widen an unmodifiable collection to @Modifiable.
  @Override
  protected void checkConstructorResult(
      AnnotatedExecutableType constructorType, ExecutableElement constructorElement) {}

  /**
   * Returns true if {@code typeElement} is a user-defined subtype of {@link java.util.Collection},
   * {@link java.util.Map}, or {@link java.util.Iterator}.
   *
   * @param typeElement the type element to test
   * @return true if {@code typeElement} is a user-defined subtype of Collection, Map, or Iterator
   */
  private boolean isCollectionFromSourceCode(TypeElement typeElement) {
    if (!ElementUtils.isElementFromSourceCode(typeElement)) {
      return false;
    }

    TypeMirror type = typeElement.asType();
    return TypesUtils.isErasedSubtype(type, collectionErasure, atypeFactory.types)
        || TypesUtils.isErasedSubtype(type, mapErasure, atypeFactory.types)
        || TypesUtils.isErasedSubtype(type, iteratorErasure, atypeFactory.types);
  }
}
