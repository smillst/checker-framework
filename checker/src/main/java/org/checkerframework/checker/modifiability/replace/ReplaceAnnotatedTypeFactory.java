package org.checkerframework.checker.modifiability.replace;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.checkerframework.checker.modifiability.ModifiabilityMethodUtils;
import org.checkerframework.checker.modifiability.qual.BottomReplaceable;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeIteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeModifiable;
import org.checkerframework.checker.modifiability.qual.MaybeReplaceable;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.PolyReplaceable;
import org.checkerframework.checker.modifiability.qual.Replaceable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.checker.modifiability.qual.Unreplaceable;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/** The annotated type factory for the {@link ReplaceChecker}. */
public class ReplaceAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

  /** The erased {@code java.util.Set} type. */
  private final TypeMirror setErasure;

  /** The erased {@code java.util.Collection} type. */
  private final TypeMirror collectionErasure;

  /** The erased {@code java.util.Queue} type. */
  private final TypeMirror queueErasure;

  /** The erased {@code java.util.LinkedList} type. */
  private final TypeMirror linkedListErasure;

  /** The erased {@code java.util.Iterator} type. */
  private final TypeMirror iteratorErasure;

  /** The erased {@code java.util.ListIterator} type. */
  private final TypeMirror listIteratorErasure;

  // -- Hierarchy qualifiers ----------

  /** The {@code @}{@link MaybeReplaceable} qualifier. */
  private final AnnotationMirror MAYBE_REPLACEABLE;

  /** The {@code @}{@link Replaceable} qualifier. */
  private final AnnotationMirror REPLACEABLE;

  /** The {@code @}{@link Unreplaceable} qualifier. */
  private final AnnotationMirror UNREPLACEABLE;

  /** The {@code @}{@link PolyReplaceable} qualifier. */
  private final AnnotationMirror POLY_REPLACEABLE;

  /** The {@code @}{@link IteratorPolyMod} qualifier. */
  private final AnnotationMirror ITERATOR_PRESERVE_REMOVE;

  /**
   * Creates a ReplaceAnnotatedTypeFactory.
   *
   * @param checker the associated type-checker
   */
  @SuppressWarnings("this-escape")
  public ReplaceAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);

    Types types = getProcessingEnv().getTypeUtils();
    this.setErasure = types.erasure(getElementUtils().getTypeElement("java.util.Set").asType());
    this.collectionErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Collection").asType());
    this.queueErasure = types.erasure(getElementUtils().getTypeElement("java.util.Queue").asType());
    this.linkedListErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.LinkedList").asType());
    this.iteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Iterator").asType());
    this.listIteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.ListIterator").asType());

    // Initialize annotation mirrors after the hierarchy is established.
    this.MAYBE_REPLACEABLE = AnnotationBuilder.fromClass(getElementUtils(), MaybeReplaceable.class);
    this.REPLACEABLE = AnnotationBuilder.fromClass(getElementUtils(), Replaceable.class);
    this.UNREPLACEABLE = AnnotationBuilder.fromClass(getElementUtils(), Unreplaceable.class);
    this.POLY_REPLACEABLE = AnnotationBuilder.fromClass(getElementUtils(), PolyReplaceable.class);
    this.ITERATOR_PRESERVE_REMOVE =
        AnnotationBuilder.fromClass(getElementUtils(), IteratorPolyMod.class);

    postInit();
  }

  @Override
  protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
    return new LinkedHashSet<>(
        Arrays.asList(
            MaybeReplaceable.class,
            Replaceable.class,
            Unreplaceable.class,
            BottomReplaceable.class,
            PolyReplaceable.class,
            MaybeIteratorPolyMod.class,
            IteratorPolyMod.class));
  }

  /**
   * Expands whole-modifiability aliases into this hierarchy, with structural weakening only for
   * aliases whose meaning depends on the annotated type.
   *
   * <p>{@code @Modifiable} and {@code @Unmodifiable} claim all component capabilities, so on types
   * that cannot replace structurally, such as exact {@code Collection}, {@code Set}, non-{@code
   * LinkedList} {@code Queue}, and non-{@code ListIterator} {@code Iterator}, their replace
   * component canonicalizes to {@code @MaybeReplaceable}.
   *
   * <p>{@code @PolyModifiable} remains {@code @PolyReplaceable}: unlike grow and shrink for {@code
   * Map.Entry}, replacement through {@code Entry.setValue} is a meaningful capability to carry from
   * the map receiver.
   */
  @Override
  public AnnotationMirror canonicalAnnotation(
      AnnotationMirror annotation, @Nullable TypeMirror tm) {
    if (tm != null) {
      if (areSameByClass(annotation, Modifiable.class)) {
        return typeCannotReplace(tm) ? MAYBE_REPLACEABLE : REPLACEABLE;
      } else if (areSameByClass(annotation, Unmodifiable.class)) {
        return typeCannotReplace(tm) ? MAYBE_REPLACEABLE : UNREPLACEABLE;
      }
    } else if (areSameByClass(annotation, MaybeModifiable.class)
        || areSameByClass(annotation, UnmodifiableParam.class)) {
      return MAYBE_REPLACEABLE;
    } else if (areSameByClass(annotation, PolyModifiable.class)) {
      return POLY_REPLACEABLE;
    }
    return super.canonicalAnnotation(annotation);
  }

  @Override
  protected ParameterizedExecutableType methodFromUse(
      MethodInvocationTree tree, boolean inferTypeArgs) {
    ParameterizedExecutableType mType = super.methodFromUse(tree, inferTypeArgs);
    AnnotatedExecutableType method = mType.executableType();

    if (isListIteratorMethod(tree, method)) {
      refineListIteratorReturnType(tree, method);
    }

    if (ModifiabilityMethodUtils.isCollectionsPlumeWithoutDuplicates(tree)) {
      refineCollectionsPlumeWithoutDuplicatesReturnType(tree, method);
    }

    return mType;
  }

  /**
   * Refines the return type of {@code CollectionsPlume.withoutDuplicates(Collection)}.
   *
   * <p>{@code withoutDuplicates} returns its argument or a new modifiable ArrayList. The signature
   *
   * <pre>{@code
   * static <T> @PolyReplaceable List<T> withoutDuplicates(@PolyReplaceable Collection<T> values)
   * }</pre>
   *
   * would be unsound, because an {@code @Unreplaceable} input could yield a replaceable ArrayList,
   * whose static type should be {@code @Replaceable}. It would be sound but imprecise to always use
   * {@code @MaybeReplaceable}, because passing a {@code @Replaceable} collection guarantees that
   * both possible results are replaceable. Therefore, model the method here as preserving
   * {@code @Replaceable} inputs and otherwise returning {@code @MaybeReplaceable}.
   *
   * @param tree an invocation of {@code withoutDuplicates}
   * @param methodType the annotated executable type of the invoked method
   */
  private void refineCollectionsPlumeWithoutDuplicatesReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror argumentType = getAnnotatedType(tree.getArguments().get(0));
    if (argumentType.hasPrimaryAnnotation(REPLACEABLE)) {
      methodType.getReturnType().replaceAnnotation(REPLACEABLE);
    }
    if (argumentType.hasPrimaryAnnotation(ITERATOR_PRESERVE_REMOVE)) {
      methodType.getReturnType().replaceAnnotation(ITERATOR_PRESERVE_REMOVE);
    }
  }

  /**
   * Refines {@code listIterator()} return type based on {@code @IteratorPolyMod}.
   *
   * <p>{@code listIterator()} cannot be annotated as {@code @PolyModifiable} because not all
   * collections preserve the modifiability of their iterators. (For example, {@code
   * CopyOnWriteArrayList} has unmodifiable iterators even though the list is modifiable.) Thus,
   * special treatment is needed for Iterator methods.
   *
   * <p>If the receiver is {@code @Replaceable} and {@code @IteratorPolyMod}, then the result is
   * {@code @Replaceable Iterator}. Otherwise, replaceability precision is dropped to
   * {@code @MaybeReplaceable}.
   *
   * @param tree the listIterator method invocation
   * @param methodType the annotated executable type of the invoked method
   */
  private void refineListIteratorReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror returnType = methodType.getReturnType();
    if (returnType.hasPrimaryAnnotation(UNREPLACEABLE)
        || returnType.hasPrimaryAnnotation(REPLACEABLE)
        || returnType.hasPrimaryAnnotation(POLY_REPLACEABLE)) {
      return;
    }

    Tree receiverTree = TreeUtils.getReceiverTree(tree);
    if (receiverTree == null) {
      return;
    }
    AnnotatedTypeMirror receiverType = getAnnotatedType(receiverTree);

    if (receiverType.hasPrimaryAnnotation(UNREPLACEABLE)) {
      returnType.replaceAnnotation(UNREPLACEABLE);
      return;
    }

    if (receiverType.hasPrimaryAnnotation(REPLACEABLE)
        && receiverType.hasPrimaryAnnotation(ITERATOR_PRESERVE_REMOVE)) {
      returnType.replaceAnnotation(REPLACEABLE);
    } else {
      returnType.replaceAnnotation(MAYBE_REPLACEABLE);
    }
  }

  /**
   * Returns true if this invocation is a {@code listIterator()} method that returns an Iterator.
   *
   * @param tree the method invocation to test
   * @param methodType the annotated executable type of the invoked method
   * @return true if this invocation returns an Iterator from {@code listIterator()}
   */
  private boolean isListIteratorMethod(
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
   * Returns true if {@code type} structurally cannot support replace operations.
   *
   * @param type the type to test
   * @return true if {@code type} structurally cannot support replace operations
   */
  private boolean typeCannotReplace(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    TypeMirror erasedType = types.erasure(type);
    return types.isSameType(erasedType, collectionErasure)
        || TypesUtils.isErasedSubtype(type, setErasure, types)
        || (TypesUtils.isErasedSubtype(type, queueErasure, types)
            && !TypesUtils.isErasedSubtype(type, linkedListErasure, types))
        || (TypesUtils.isErasedSubtype(type, iteratorErasure, types)
            && !TypesUtils.isErasedSubtype(type, listIteratorErasure, types));
  }
}
