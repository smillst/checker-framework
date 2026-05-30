package org.checkerframework.checker.modifiability;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/** Shared annotated type factory logic for the Modifiability sub-checkers. */
public abstract class ModifiabilityAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

  /** The erased {@code java.util.Iterator} type. */
  private final TypeMirror iteratorErasure;

  /** The erased {@code java.util.ListIterator} type. */
  private final TypeMirror listIteratorErasure;

  /**
   * Creates a ModifiabilityAnnotatedTypeFactory.
   *
   * @param checker the associated type-checker
   */
  @SuppressWarnings("this-escape")
  protected ModifiabilityAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);

    Types types = getProcessingEnv().getTypeUtils();
    this.iteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Iterator").asType());
    this.listIteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.ListIterator").asType());
  }

  /**
   * Refines the return type of a {@code @PreservesModifiability} method.
   *
   * <p>If the method has no parameters, then this annotation has no effect.
   *
   * <p>Otherwise, if the first argument is positive (i.e.{@code @Shrinkable}), then the return type
   * is also positive. If the first argument is {@code @IteratorPolyMod}, then the return type is
   * also {@code @IteratorPolyMod}.
   *
   * <p>For every other case, the return type is top ({@code @Maybe*}).
   *
   * <p>Such method cannot be annotated as {@code @Poly*} because an negative (e.g.
   * {@code @Unshrinkable}) input could yield either a shrinkable or unshrinkable result. It would
   * be imprecise to always use {@code @Maybe*}, because passing a positive type collection
   * guarantees a positive return type.
   *
   * <p>This method is called by all three sub-checkers.
   *
   * @param tree an invocation of a {@code @PreservesModifiability} method
   * @param methodType the annotated executable type of the invoked method
   */
  protected void refinePreservesModifiabilityReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    if (tree.getArguments().isEmpty()) {
      return;
    }
    AnnotatedTypeMirror argumentType = getAnnotatedType(tree.getArguments().get(0));
    if (argumentType.hasPrimaryAnnotation(positiveCapability())) {
      methodType.getReturnType().replaceAnnotation(positiveCapability());
    }
    if (argumentType.hasPrimaryAnnotation(iteratorPreserveRemove())) {
      methodType.getReturnType().replaceAnnotation(iteratorPreserveRemove());
    }
  }

  /**
   * Refines {@code iterator()} and {@code listIterator()} return types based on
   * {@code @IteratorPolyMod}.
   *
   * <p>{@code iterator()} and {@code listIterator()} cannot be annotated as {@code @PolyModifiable}
   * because not all collections preserve the modifiability of their iterators. (For example, {@code
   * CopyOnWriteArrayList} has unmodifiable iterators even though the list is modifiable.) Thus,
   * special treatment is needed for Iterator methods.
   *
   * <p>If the receiver is {@code @Modifiable} and {@code @IteratorPolyMod}, then the result is
   * {@code @Modifiable Iterator}. Otherwise, shrinkability precision is dropped to
   * {@code @MaybeModifiable}.
   *
   * <p>This method is called by all three sub-checkers. In ShrinkAnnotatedTypeFactory, this method
   * is called by both {@code iterator()} and {@code listIterator()} methods. In
   * GrowAnnotatedTypeFactory and ReplaceAnnotatedTypeFactory, this method is only called by {@code
   * listIterator()} methods.
   *
   * @param tree the iterator method invocation
   * @param methodType the annotated executable type of the invoked method
   */
  protected void refineIteratorReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror returnType = methodType.getReturnType();
    // Keep explicit ungrowable/growable iterator contracts (for example, CopyOnWriteArrayList,
    // ArrayList).
    if (returnType.hasPrimaryAnnotation(negativeCapability())
        || returnType.hasPrimaryAnnotation(positiveCapability())
        || returnType.hasPrimaryAnnotation(polyCapability())) {
      return;
    }

    Tree receiverTree = TreeUtils.getReceiverTree(tree);
    if (receiverTree == null) {
      return;
    }
    AnnotatedTypeMirror receiverType = getAnnotatedType(receiverTree);

    // all ungrowable collections' iterators are ungrowable.
    if (receiverType.hasPrimaryAnnotation(negativeCapability())) {
      returnType.replaceAnnotation(negativeCapability());
      return;
    }

    // receiver type is @Modifiable and @IteratorPolyMod
    if (receiverType.hasPrimaryAnnotation(positiveCapability())
        && receiverType.hasPrimaryAnnotation(iteratorPreserveRemove())) {
      returnType.replaceAnnotation(positiveCapability());
    }
  }

  /**
   * Returns true if this invocation is a {@code listIterator()} method that returns a ListIterator.
   *
   * @param tree the method invocation to test
   * @param methodType the annotated executable type of the invoked method
   * @return true if this invocation returns a ListIterator from {@code listIterator()}
   */
  protected boolean isListIteratorMethod(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    ExecutableElement invokedMethod = TreeUtils.elementFromUse(tree);
    if (invokedMethod == null) {
      return false;
    }
    // quick syntax check before expensive erasure checks
    if (!invokedMethod.getSimpleName().contentEquals("listIterator")
        || tree.getArguments().size() > 1) {
      return false;
    }
    // Check if the return type is an erased ListIterator
    TypeMirror returnUnderlying = methodType.getReturnType().getUnderlyingType();
    return TypesUtils.isErasedSubtype(returnUnderlying, listIteratorErasure, types);
  }

  /**
   * Returns true if this invocation is an iterator-returning {@code iterator()} or {@code
   * listIterator()} method.
   *
   * @param tree the method invocation to test
   * @param methodType the annotated executable type of the invoked method
   * @return true if this invocation returns an Iterator from {@code iterator()} or {@code
   *     listIterator()}
   */
  protected boolean isIteratorMethod(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    ExecutableElement invokedMethod = TreeUtils.elementFromUse(tree);
    if (invokedMethod == null) {
      return false;
    }
    // quick syntax check before expensive erasure checks
    String methodName = invokedMethod.getSimpleName().toString();
    int argCount = tree.getArguments().size();
    if (!((methodName.equals("iterator") && argCount == 0)
        || (methodName.equals("listIterator") && argCount <= 1))) {
      return false;
    }
    // Check if the return type is an erased Iterator
    TypeMirror returnUnderlying = methodType.getReturnType().getUnderlyingType();
    return TypesUtils.isErasedSubtype(returnUnderlying, iteratorErasure, types);
  }

  /** Returns the top/unknown qualifier. */
  protected abstract AnnotationMirror maybeCapability();

  /** Returns the positive capability qualifier. */
  protected abstract AnnotationMirror positiveCapability();

  /** Returns the negative capability qualifier. */
  protected abstract AnnotationMirror negativeCapability();

  /** Returns the polymorphic capability qualifier. */
  protected abstract AnnotationMirror polyCapability();

  /** Returns the {@code @}{@link IteratorPolyMod} qualifier. */
  protected abstract AnnotationMirror iteratorPreserveRemove();
}
