package org.checkerframework.checker.modifiability;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.framework.source.SourceVisitor;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TreeUtils;

/** Visitor for the aggregate ModifiabilityChecker. */
public class ModifiabilityVisitor extends SourceVisitor<Void, Void> {

  /** Method-scoped {@code @UnmodifiableParam} annotations allowed by their parameter location. */
  private final Deque<Set<AnnotationTree>> allowedUnmodifiableParamAnnotations = new ArrayDeque<>();

  /** {@link ModifiabilityChecker}. */
  private final ModifiabilityChecker checker;

  /**
   * Creates a {@link SourceVisitor} to use for scanning a source tree.
   *
   * @param checker the checker to invoke on the input source tree
   */
  protected ModifiabilityVisitor(ModifiabilityChecker checker) {
    super(checker);
    this.checker = checker;
  }

  /**
   * Collects the {@code @UnmodifiableParam} annotations that are permitted in this method's formal
   * and receiver parameters before visiting the method body. {@link #visitAnnotation} uses the
   * stack entry to distinguish allowed parameter annotations from disallowed uses elsewhere in the
   * same method.
   */
  @Override
  public Void visitMethod(MethodTree tree, Void unused) {
    Set<AnnotationTree> allowedAnnotations = new HashSet<>();
    for (VariableTree parameter : tree.getParameters()) {
      allowedAnnotations.addAll(unmodifiableParamAnnotations(parameter));
    }
    VariableTree receiverParameter = tree.getReceiverParameter();
    if (receiverParameter != null) {
      allowedAnnotations.addAll(unmodifiableParamAnnotations(receiverParameter));
    }
    allowedUnmodifiableParamAnnotations.push(allowedAnnotations);
    super.visitMethod(tree, unused);
    allowedUnmodifiableParamAnnotations.pop();
    return null;
  }

  @Override
  public Void visitAnnotation(AnnotationTree tree, Void p) {
    if (isUnmodifiableParamAnnotation(tree)) {
      if (allowedUnmodifiableParamAnnotations.isEmpty()
          || !allowedUnmodifiableParamAnnotations.getFirst().contains(tree)) {
        checker.reportError(tree, "unmodparam.location");
      }
    }
    return super.visitAnnotation(tree, p);
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

  /** Fully-qualified name for {@link UnmodifiableParam}. */
  private static final String unmodifiableParamQualifiedName = UnmodifiableParam.class.getName();

  /**
   * Returns true if {@code tree} is an {@code @}{@link UnmodifiableParam} annotation.
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

    return annotation != null
        && AnnotationUtils.areSameByName(annotation, unmodifiableParamQualifiedName);
  }
}
