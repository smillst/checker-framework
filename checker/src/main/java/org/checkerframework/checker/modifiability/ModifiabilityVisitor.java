package org.checkerframework.checker.modifiability;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
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
 *   <li>Suppressing the "constructor result must be TOP" check, since collection constructors may
 *       legitimately produce {@code @Modifiable}.
 * </ul>
 */
public class ModifiabilityVisitor extends BaseTypeVisitor<ModifiabilityAnnotatedTypeFactory> {

  /** The erased {@code java.util.Collection} type. */
  private final TypeMirror collectionErasure;

  /** The erased {@code java.util.Map} type. */
  private final TypeMirror mapErasure;

  /** The erased {@code java.util.Iterator} type. */
  private final TypeMirror iteratorErasure;

  /** Method-scoped {@code @UnmodifiableParam} annotations allowed by their parameter location. */
  private final Deque<Set<AnnotationTree>> allowedUnmodifiableParamAnnotations = new ArrayDeque<>();

  /** Package that contains the modifiability type-use qualifiers. */
  private static final String MODIFIABILITY_QUAL_PACKAGE =
      "org.checkerframework.checker.modifiability.qual";

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

  /**
   * Processes a class declaration and reports an unverified modifiability warning only when a
   * source-defined collection-like type explicitly claims a concrete modifiability qualifier on the
   * class or one of its constructors. Defaulted/unannotated types and Maybe* annotations do not
   * make a verifiable modifiability claim.
   *
   * @param classTree the class declaration to process
   */
  @Override
  public void processClassTree(ClassTree classTree) {
    super.processClassTree(classTree);
    if (shouldCheckCustomModifiabilityAnnotation()) {
      TypeElement typeElement = TreeUtils.elementFromDeclaration(classTree);
      if (typeElement != null
          && isCollectionFromSourceCode(typeElement)
          && hasExplicitWarningModifiabilityAnnotation(classTree)) {
        checker.reportWarning(
            classTree, "modifiability.annotation.unverified", typeElement.getQualifiedName());
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
    if (shouldCheckUnmodifiableParamLocation() && isUnmodifiableParamAnnotation(tree)) {
      if (allowedUnmodifiableParamAnnotations.isEmpty()
          || !allowedUnmodifiableParamAnnotations.peek().contains(tree)) {
        checker.reportError(tree, "unmodparam.location");
      }
    }
    return super.visitAnnotation(tree, p);
  }

  /**
   * Returns the positive qualifier for this checker's modifiability hierarchy, such as
   * {@code @Growable}, {@code @Shrinkable}, or {@code @Replaceable}.
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
   * Returns true if {@code classTree} explicitly writes a warning-worthy modifiability qualifier on
   * the class declaration or on any constructor.
   *
   * @param classTree a class declaration
   * @return true if a warning-worthy modifiability annotation is written on the class or
   *     constructor
   */
  private boolean hasExplicitWarningModifiabilityAnnotation(ClassTree classTree) {
    // write explicit modifianility annotations on the class
    if (hasWarningModifiabilityAnnotation(classTree.getModifiers().getAnnotations())) {
      return true;
    }

    // write explicit modifiability annotations on any constructor
    for (Tree member : classTree.getMembers()) {
      if (member instanceof MethodTree methodTree
          && TreeUtils.isConstructor(methodTree)
          && hasWarningModifiabilityAnnotation(methodTree.getModifiers().getAnnotations())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if the list {@code annotations} contains any explicit non-maybe modifiability
   * qualifier.
   *
   * <p>Iterator throw the list of annotations and call isWarningModifiablityAnnotation(annotation)
   *
   * @param annotations annotation trees to inspect
   * @return true if one annotation should trigger an unverified modifiability warning
   */
  private boolean hasWarningModifiabilityAnnotation(
      Iterable<? extends AnnotationTree> annotations) {
    for (AnnotationTree annotationTree : annotations) {
      AnnotationMirror annotation = TreeUtils.annotationFromAnnotationTree(annotationTree);
      if (annotation != null && isWarningModifiabilityAnnotation(annotation)) {
        return true;
      }
    }
    return false;
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
    if (simpleName.startsWith("Maybe") || simpleName.equals("UnmodifiableParam")) {
      return false;
    }

    return true;
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
    scanner.scan(parameter.getModifiers().getAnnotations(), null);
    if (parameter.getType() != null) {
      // search for method(List<@UnmodifiableParam List<>> param)
      scanner.scan(parameter.getType(), null);
    }
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
