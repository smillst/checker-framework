package org.checkerframework.checker.modifiability.grow;

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
import org.checkerframework.checker.modifiability.qual.BottomGrowable;
import org.checkerframework.checker.modifiability.qual.Growable;
import org.checkerframework.checker.modifiability.qual.IteratorPolyShrinkable;
import org.checkerframework.checker.modifiability.qual.MaybeGrowable;
import org.checkerframework.checker.modifiability.qual.MaybeIteratorPolyShrinkable;
import org.checkerframework.checker.modifiability.qual.MaybeModifiable;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyGrowable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.Ungrowable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/** The annotated type factory for the {@link GrowChecker}. */
public class GrowAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

  /** The erased {@code java.util.Map.Entry} type. */
  private final TypeMirror mapEntryErasure;

  /** The erased {@code java.util.Iterator} type. */
  private final TypeMirror iteratorErasure;

  /** The erased {@code java.util.ListIterator} type. */
  private final TypeMirror listIteratorErasure;

  // -- Hierarchy qualifiers ----------

  /** The {@code @}{@link MaybeGrowable} qualifier. */
  private final AnnotationMirror MAYBE_GROWABLE;

  /** The {@code @}{@link Growable} qualifier. */
  private final AnnotationMirror GROWABLE;

  /** The {@code @}{@link Ungrowable} qualifier. */
  private final AnnotationMirror UNGROWABLE;

  /** The {@code @}{@link PolyGrowable} qualifier. */
  private final AnnotationMirror POLY_GROWABLE;

  /** The {@code @}{@link IteratorPolyShrinkable} qualifier. */
  private final AnnotationMirror ITERATOR_PRESERVE_REMOVE;

  /**
   * Creates a GrowAnnotatedTypeFactory.
   *
   * @param checker the associated type-checker
   */
  @SuppressWarnings("this-escape")
  public GrowAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);

    Types types = getProcessingEnv().getTypeUtils();
    this.mapEntryErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Map.Entry").asType());
    this.iteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Iterator").asType());
    this.listIteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.ListIterator").asType());
    // Initialize annotation mirrors after the hierarchy is established.
    this.MAYBE_GROWABLE = AnnotationBuilder.fromClass(getElementUtils(), MaybeGrowable.class);
    this.GROWABLE = AnnotationBuilder.fromClass(getElementUtils(), Growable.class);
    this.UNGROWABLE = AnnotationBuilder.fromClass(getElementUtils(), Ungrowable.class);
    this.POLY_GROWABLE = AnnotationBuilder.fromClass(getElementUtils(), PolyGrowable.class);
    this.ITERATOR_PRESERVE_REMOVE =
        AnnotationBuilder.fromClass(getElementUtils(), IteratorPolyShrinkable.class);

    postInit();
  }

  @Override
  protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
    return new LinkedHashSet<>(
        Arrays.asList(
            MaybeGrowable.class,
            Growable.class,
            Ungrowable.class,
            BottomGrowable.class,
            PolyGrowable.class,
            MaybeIteratorPolyShrinkable.class,
            IteratorPolyShrinkable.class));
  }

  /**
   * Expands whole-modifiability aliases into this hierarchy, with structural weakening only for
   * aliases whose meaning depends on the annotated type.
   *
   * <p>{@code @Modifiable} and {@code @Unmodifiable} claim all component capabilities, so on types
   * that cannot grow structurally, such as {@code Map.Entry} and non-{@code ListIterator} {@code
   * Iterator}, their grow component canonicalizes to {@code @MaybeGrowable}.
   *
   * <p>{@code @PolyModifiable} is different: it should usually become {@code @PolyGrowable}, but
   * for {@code Map.Entry} only the replace bit is meaningful to carry from the map receiver. Its
   * grow bit is therefore {@code @MaybeGrowable}.
   */
  @Override
  public AnnotationMirror canonicalAnnotation(
      AnnotationMirror annotation, @Nullable TypeMirror tm) {
    if (tm != null) {
      if (areSameByClass(annotation, Modifiable.class)) {
        return typeCannotGrow(tm) ? MAYBE_GROWABLE : GROWABLE;
      } else if (areSameByClass(annotation, Unmodifiable.class)) {
        return typeCannotGrow(tm) ? MAYBE_GROWABLE : UNGROWABLE;
      } else if (areSameByClass(annotation, PolyModifiable.class)) {
        return isMapEntry(tm) ? MAYBE_GROWABLE : POLY_GROWABLE;
      }
    } else if (areSameByClass(annotation, MaybeModifiable.class)
        || areSameByClass(annotation, UnmodifiableParam.class)) {
      return MAYBE_GROWABLE;
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
   * static <T> @PolyGrowable List<T> withoutDuplicates(@PolyGrowable Collection<T> values)
   * }</pre>
   *
   * would be unsound, because an {@code @Ungrowable} input could yield a growable ArrayList, whose
   * static type should be {@code @Growable}. It would be sound but imprecise to always use
   * {@code @MaybeGrowable}, because passing a {@code @Growable} collection guarantees that both
   * possible results are growable. Therefore, model the method here as preserving {@code @Growable}
   * inputs and otherwise returning {@code @MaybeGrowable}.
   *
   * @param tree an invocation of {@code withoutDuplicates}
   * @param methodType the annotated executable type of the invoked method
   */
  private void refineCollectionsPlumeWithoutDuplicatesReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror argumentType = getAnnotatedType(tree.getArguments().get(0));
    if (argumentType.hasPrimaryAnnotation(GROWABLE)) {
      methodType.getReturnType().replaceAnnotation(GROWABLE);
    }
    if (argumentType.hasPrimaryAnnotation(ITERATOR_PRESERVE_REMOVE)) {
      methodType.getReturnType().replaceAnnotation(ITERATOR_PRESERVE_REMOVE);
    }
  }

  /**
   * Refines {@code listIterator()} return type based on {@code @IteratorPolyShrinkable}.
   *
   * @param tree the listIterator method invocation
   * @param methodType the annotated executable type of the invoked method
   */
  private void refineListIteratorReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror returnType = methodType.getReturnType();
    if (returnType.hasPrimaryAnnotation(UNGROWABLE)
        || returnType.hasPrimaryAnnotation(GROWABLE)
        || returnType.hasPrimaryAnnotation(POLY_GROWABLE)) {
      return;
    }

    Tree receiverTree = TreeUtils.getReceiverTree(tree);
    if (receiverTree == null) {
      return;
    }
    AnnotatedTypeMirror receiverType = getAnnotatedType(receiverTree);

    if (receiverType.hasPrimaryAnnotation(UNGROWABLE)) {
      returnType.replaceAnnotation(UNGROWABLE);
      return;
    }

    if (!receiverType.hasPrimaryAnnotation(GROWABLE)) {
      return;
    }

    if (hasIteratorPolyShrinkable(receiverType)) {
      returnType.replaceAnnotation(GROWABLE);
    } else {
      returnType.replaceAnnotation(MAYBE_GROWABLE);
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
    if (!invokedMethod.getSimpleName().contentEquals("listIterator")
        || tree.getArguments().size() > 1) {
      return false;
    }

    TypeMirror returnUnderlying = methodType.getReturnType().getUnderlyingType();
    return TypesUtils.isErasedSubtype(returnUnderlying, iteratorErasure, types);
  }

  /**
   * Returns true if {@code type} has the {@code @IteratorPolyShrinkable} marker annotation.
   *
   * @param type the type to test
   * @return true if {@code type} has the {@code @IteratorPolyShrinkable} marker annotation
   */
  private boolean hasIteratorPolyShrinkable(AnnotatedTypeMirror type) {
    if (type.hasPrimaryAnnotation(ITERATOR_PRESERVE_REMOVE)) {
      return true;
    }
    return AnnotationUtils.containsSameByClass(
        type.getUnderlyingType().getAnnotationMirrors(), IteratorPolyShrinkable.class);
  }

  /**
   * Returns true if {@code type} structurally cannot support grow operations.
   *
   * @param type the type to test
   * @return true if {@code type} structurally cannot support grow operations
   */
  private boolean typeCannotGrow(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    return isMapEntry(type)
        || (TypesUtils.isErasedSubtype(type, iteratorErasure, types)
            && !TypesUtils.isErasedSubtype(type, listIteratorErasure, types));
  }

  /**
   * Returns true if {@code type} is a subtype of {@link java.util.Map.Entry}.
   *
   * @param type the type to test
   * @return true if {@code type} is a subtype of {@link java.util.Map.Entry}
   */
  private boolean isMapEntry(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    return TypesUtils.isErasedSubtype(type, mapEntryErasure, types);
  }
}
