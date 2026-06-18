package org.checkerframework.checker.modifiability;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationMirrorSet;
import org.checkerframework.javacutil.TreePathUtil;
import org.checkerframework.javacutil.TreeUtils;

/**
 * Base visitor for the modifiability sub-checkers (Grow, SeqGrow, Shrink, Replace).
 *
 * <p>This class contains logic shared across all four sub-checkers:
 *
 * <ul>
 *   <li>Suppressing the "constructor result must be TOP" check, since collection constructors may
 *       legitimately produce {@code @Modifiable}.
 * </ul>
 */
public class ModifiabilityBaseVisitor
    extends BaseTypeVisitor<ModifiabilityBaseAnnotatedTypeFactory> {

  /** Method-scoped {@code @UnmodifiableParam} annotations allowed by their parameter location. */
  private final Deque<Set<AnnotationTree>> allowedUnmodifiableParamAnnotations = new ArrayDeque<>();

  /** Package that contains the modifiability type-use qualifiers. */
  private static final String MODIFIABILITY_QUAL_PACKAGE =
      "org.checkerframework.checker.modifiability.qual";

  /**
   * Create a ModifiabilityBaseVisitor.
   *
   * @param checker the checker that uses this visitor
   */
  public ModifiabilityBaseVisitor(BaseTypeChecker checker) {
    super(checker);
  }

  @Override
  protected void checkThisOrSuperConstructorCall(
      MethodInvocationTree call, @CompilerMessageKey String errorKey) {

    TreePath path = atypeFactory.getPath(call);
    MethodTree enclosingMethod = TreePathUtil.enclosingMethod(path);
    AnnotatedTypeMirror superType = atypeFactory.getAnnotatedType(call);
    AnnotatedExecutableType constructorType = atypeFactory.getAnnotatedType(enclosingMethod);
    AnnotatedTypeMirror returnType = constructorType.getReturnType();
    AnnotationMirrorSet topAnnotations = qualHierarchy.getTopAnnotations();
    for (AnnotationMirror topAnno : topAnnotations) {
      if (!typeHierarchy.isSubtypeShallowEffective(superType, returnType, topAnno)) {
        AnnotationMirror superAnno = superType.getPrimaryAnnotationInHierarchy(topAnno);
        AnnotationMirror constructorReturnAnno =
            returnType.getPrimaryAnnotationInHierarchy(topAnno);
        checker.reportError(call, errorKey, constructorReturnAnno, call, superAnno);
      } else if (isWarningModifiabilityAnnotation(returnType.getAnnotationInHierarchy(topAnno))) {
        checker.reportWarning(
            call, "modifiability.annotation.unverified", returnType.getUnderlyingType());
      }
    }
  }

  /**
   * Collects the {@code @UnmodifiableParam} annotations that are permitted in this method's formal
   * and receiver parameters before visiting the method body. {@link #visitAnnotation} uses the
   * stack entry to distinguish allowed parameter annotations from disallowed uses elsewhere in the
   * same method.
   */
  @Override
  public void processMethodTree(String className, MethodTree tree) {
    Set<AnnotationTree> allowedAnnotations = new HashSet<>();
    for (VariableTree parameter : tree.getParameters()) {
      allowedAnnotations.addAll(unmodifiableParamAnnotations(parameter));
    }
    VariableTree receiverParameter = tree.getReceiverParameter();
    if (receiverParameter != null) {
      allowedAnnotations.addAll(unmodifiableParamAnnotations(receiverParameter));
    }
    allowedUnmodifiableParamAnnotations.push(allowedAnnotations);
    super.processMethodTree(className, tree);
    allowedUnmodifiableParamAnnotations.pop();
  }

  @Override
  public Void visitAnnotation(AnnotationTree tree, Void p) {
    if (shouldCheckModifiabilityAnnotationValidity() && isUnmodifiableParamAnnotation(tree)) {
      if (allowedUnmodifiableParamAnnotations.isEmpty()
          || !allowedUnmodifiableParamAnnotations.getFirst().contains(tree)) {
        checker.reportError(tree, "unmodparam.location");
      }
    }
    return super.visitAnnotation(tree, p);
  }

  /**
   * Returns the positive qualifier for this checker's modifiability hierarchy, such as
   * {@code @Growable}, {@code @SeqGrowable}, {@code @Shrinkable}, or {@code @Replaceable}.
   *
   * @return this checker's positive capability qualifier
   */
  private AnnotationMirror positiveCapability() {
    return atypeFactory.positiveCapability();
  }

  /**
   * Checks the normal override rules, then requires overrides to preserve any positive
   * modifiability receiver capability from the overridden method.
   *
   * <p>For example, if the overridden method requires a {@code @Growable} receiver, then the
   * overriding method must also require a {@code @Growable} receiver.
   *
   * <p>The framework's ordinary receiver override rule allows an overriding method to relax
   * receiver preconditions. For modifiability operations, that would allow a subtype method to drop
   * a required {@code @Growable}, {@code @Shrinkable}, or {@code @Replaceable} receiver capability.
   *
   * <p>For example:
   *
   * <pre>{@code
   * class List {
   *   void add(@Growable List<String> list) {
   *     // ...
   *   }
   * }
   *
   * class A extends List {
   *   @Override
   *   void add() {
   *     throw new UnsupportedOperationException();
   *   }
   * }
   * }</pre>
   *
   * <p>Without requiring the override to preserve the {@code @Growable} receiver, {@code A.add()}
   * would be permitted even when {@code A} is only {@code @MaybeMod}.
   */
  @Override
  protected boolean checkOverride(
      MethodTree overriderTree,
      AnnotatedExecutableType overriderMethodType,
      AnnotatedDeclaredType overriderType,
      AnnotatedExecutableType overriddenMethodType,
      AnnotatedDeclaredType overriddenType) {
    if (!super.checkOverride(
        overriderTree, overriderMethodType, overriderType, overriddenMethodType, overriddenType)) {
      return false;
    }
    // Only capability checkers need to preserve receiver capabilities in overrides.
    // @IteratorPolyMod does not follow this special override rule.
    if (!shouldCheckReceiverOverrideCapabilityPreservation()) {
      return true;
    }

    AnnotatedDeclaredType overriderReceiver = overriderMethodType.getReceiverType();
    AnnotatedDeclaredType overriddenReceiver = overriddenMethodType.getReceiverType();
    if (overriderReceiver == null || overriddenReceiver == null) {
      return true;
    }

    AnnotationMirror positiveCapability = positiveCapability();

    if (overriddenReceiver.hasPrimaryAnnotation(positiveCapability)
        && !overriderReceiver.hasPrimaryAnnotation(positiveCapability)) {
      checker.reportError(
          overriderTree,
          "override.receiver",
          overriderReceiver,
          overriddenReceiver,
          overriderType,
          overriderMethodType,
          overriddenType,
          overriddenMethodType);
      return false;
    }
    return true;
  }

  /**
   * Returns true if overrides should preserve positive receiver capabilities from overridden
   * methods.
   *
   * @return true if overrides should preserve positive receiver capabilities
   */
  protected boolean shouldCheckReceiverOverrideCapabilityPreservation() {
    return true;
  }

  /**
   * Returns true if this checker should report diagnostics about modifiability annotations whose
   * validity is independent of a particular capability hierarchy.
   *
   * <p>The default is {@code true}. SeqGrow, Shrink, Replace, and Iterator override this to avoid
   * repeating diagnostics when running under the aggregate {@link ModifiabilityChecker}.
   *
   * @return true if this visitor should report shared modifiability annotation diagnostics
   */
  protected boolean shouldCheckModifiabilityAnnotationValidity() {
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
   * Returns true if {@code annotation} is a non-maybe modifiability qualifier that should trigger
   * the unverified custom modifiability warning.
   *
   * @param annotation an annotation mirror
   * @return true if {@code annotation} should trigger a warning
   */
  private boolean isWarningModifiabilityAnnotation(AnnotationMirror annotation) {
    Element element = annotation.getAnnotationType().asElement();
    if (!(element instanceof TypeElement typeElement)) {
      return false;
    }

    // Only annotations in the modifiability qualifier package are relevant.
    String qualifiedName = typeElement.getQualifiedName().toString();
    if (!qualifiedName.startsWith(MODIFIABILITY_QUAL_PACKAGE + ".")) {
      return false;
    }

    // Maybe* annotations and @UnmodifiableParam are unknown/top-like, so they do not warn.
    String simpleName = typeElement.getSimpleName().toString();
    return !simpleName.startsWith("Maybe") && !simpleName.equals("UnmodifiableParam");
  }

  /**
   * Returns all {@code @UnmodifiableParam} annotations written on a formal or receiver parameter's
   * type. An annotation before the parameter type may appear in the parameter's modifiers, while an
   * annotation inside a generic or array type appears in the parameter's type tree.
   *
   * @param parameter a formal or receiver parameter
   * @return the {@code @UnmodifiableParam} annotation trees that are allowed by this parameter
   *     location
   */
  private Set<AnnotationTree> unmodifiableParamAnnotations(VariableTree parameter) {
    Set<AnnotationTree> annotations = new HashSet<>();
    TreeScanner<Void, Void> scanner =
        new TreeScanner<>() {
          @Override
          public Void visitAnnotation(AnnotationTree tree, Void p) {
            if (isUnmodifiableParamAnnotation(tree)) {
              annotations.add(tree);
            }
            return super.visitAnnotation(tree, p);
          }
        };

    // Scan both javac locations for parameter type annotations.
    // search for method(@UnmodifiableParam List<> param)
    scanner.scan(parameter, null);
    return annotations;
  }

  // Suppresses the framework's "constructor result must be TOP" check.
  // Collection constructors (e.g., new ArrayList()) legitimately produce @Modifiable, which is a
  // subtype of the top type @MaybeModifiable. This suppression is sound: constructors are
  // typed by their stubs or by defaults inferred from the class's annotations, so they cannot
  // silently widen an unmodifiable collection to @Modifiable.
  @Override
  protected void checkConstructorResult(
      AnnotatedExecutableType constructorType, ExecutableElement constructorElement) {}
}
