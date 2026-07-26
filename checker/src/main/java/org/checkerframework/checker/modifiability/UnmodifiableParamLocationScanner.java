package org.checkerframework.checker.modifiability;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Name;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TreeUtils;

/**
 * Issues an error for every {@code @}{@link UnmodifiableParam} annotation that is not written on a
 * formal parameter or a receiver parameter.
 *
 * <p>This check does not depend on any particular modifiability hierarchy, so exactly one checker
 * performs it: the aggregate {@link ModifiabilityChecker} when it is run (see {@link
 * ModifiabilityVisitor}), and otherwise the outermost modifiability sub-checker (see {@link
 * ModifiabilityBaseVisitor#visit}).
 *
 * <p>Scan a top-level type declaration by calling {@code scan}. One scanner may be reused for every
 * declaration that its checker processes.
 */
class UnmodifiableParamLocationScanner extends TreeScanner<Void, Void> {

  /**
   * The fully-qualified name of {@link UnmodifiableParam}. {@code UnmodifiableParam} is a top-level
   * type, so its fully-qualified name is also its canonical name.
   */
  private static final String UNMODIFIABLE_PARAM_NAME = UnmodifiableParam.class.getName();

  /** The simple name of {@link UnmodifiableParam}. */
  private static final String UNMODIFIABLE_PARAM_SIMPLE_NAME =
      UnmodifiableParam.class.getSimpleName();

  /** The checker that issues the errors. */
  private final SourceChecker checker;

  /**
   * A stack, with one element per enclosing method or constructor, of the
   * {@code @UnmodifiableParam} annotation trees that the method's formal and receiver parameters
   * permit. The stack is empty while the scan is not within a method.
   */
  private final Deque<Set<AnnotationTree>> allowedUnmodifiableParamAnnotations = new ArrayDeque<>();

  /**
   * Collects the annotations returned by {@link #allowedAnnotations}. This is a field rather than a
   * local variable so that scanning a method's parameters allocates nothing in the common case.
   * Reuse is safe because a parameter declaration cannot contain a method declaration, so {@link
   * #allowedAnnotations} is never called while it is already running.
   */
  private final ParameterAnnotationCollector collector = new ParameterAnnotationCollector();

  /**
   * Creates an UnmodifiableParamLocationScanner.
   *
   * @param checker the checker that issues the errors
   */
  UnmodifiableParamLocationScanner(SourceChecker checker) {
    this.checker = checker;
  }

  @Override
  public Void visitMethod(MethodTree tree, Void p) {
    allowedUnmodifiableParamAnnotations.push(allowedAnnotations(tree));
    try {
      return super.visitMethod(tree, p);
    } finally {
      allowedUnmodifiableParamAnnotations.pop();
    }
  }

  @Override
  public Void visitAnnotation(AnnotationTree tree, Void p) {
    if (isUnmodifiableParamAnnotation(tree)
        && (allowedUnmodifiableParamAnnotations.isEmpty()
            || !allowedUnmodifiableParamAnnotations.getFirst().contains(tree))) {
      checker.reportError(tree, "unmodparam.location");
    }
    return super.visitAnnotation(tree, p);
  }

  /**
   * Returns the {@code @UnmodifiableParam} annotation trees that {@code tree}'s formal and receiver
   * parameters permit.
   *
   * <p>An annotation written before a parameter's type appears in the parameter's modifiers, and an
   * annotation written within a generic or array type appears in the parameter's type tree, so this
   * scans the entire parameter declaration.
   *
   * @param tree a method or constructor declaration
   * @return the {@code @UnmodifiableParam} annotation trees that {@code tree}'s formal and receiver
   *     parameters permit; an empty set if there are none
   */
  private Set<AnnotationTree> allowedAnnotations(MethodTree tree) {
    collector.found = Collections.emptySet();
    collector.scan(tree.getParameters(), null);
    collector.scan(tree.getReceiverParameter(), null);
    return collector.found;
  }

  /**
   * Returns true if {@code tree} is an {@code @}{@link UnmodifiableParam} annotation.
   *
   * @param tree an annotation tree
   * @return true if {@code tree} is an {@code @UnmodifiableParam} annotation
   */
  private static boolean isUnmodifiableParamAnnotation(AnnotationTree tree) {
    // Quick check of the simple name, to avoid expensive annotation resolution for most
    // annotations.  This avoids `toString()`, which would allocate a string for every annotation in
    // the program.
    Name simpleName;
    Tree annotationType = tree.getAnnotationType();
    if (annotationType instanceof IdentifierTree it) {
      simpleName = it.getName();
    } else if (annotationType instanceof MemberSelectTree mst) {
      simpleName = mst.getIdentifier();
    } else {
      // An annotation type is an identifier or a member select, unless the program does not
      // compile.
      return false;
    }
    if (!simpleName.contentEquals(UNMODIFIABLE_PARAM_SIMPLE_NAME)) {
      return false;
    }

    AnnotationMirror annotation = TreeUtils.annotationFromAnnotationTree(tree);
    return annotation != null && AnnotationUtils.areSameByName(annotation, UNMODIFIABLE_PARAM_NAME);
  }

  /**
   * A scanner that collects, into {@link #found}, the {@code @UnmodifiableParam} annotations
   * written within a formal or receiver parameter declaration.
   */
  private static class ParameterAnnotationCollector extends TreeScanner<Void, Void> {

    /**
     * The annotations found so far. This is an immutable empty set until the first annotation is
     * found, so that the common case of a parameter list containing no {@code @UnmodifiableParam}
     * annotation allocates nothing.
     */
    private Set<AnnotationTree> found = Collections.emptySet();

    /** Creates a ParameterAnnotationCollector. */
    ParameterAnnotationCollector() {}

    @Override
    public Void visitAnnotation(AnnotationTree tree, Void p) {
      if (isUnmodifiableParamAnnotation(tree)) {
        if (found.isEmpty()) {
          found = new HashSet<>(4);
        }
        found.add(tree);
      }
      return super.visitAnnotation(tree, p);
    }
  }
}
