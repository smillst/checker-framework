package org.checkerframework.checker.modifiability.shrink;

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
import org.checkerframework.checker.modifiability.qual.BottomShrink;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeIteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeModifiable;
import org.checkerframework.checker.modifiability.qual.MaybeShrink;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.PolyShrink;
import org.checkerframework.checker.modifiability.qual.Shrinkable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.checker.modifiability.qual.Unshrinkable;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeFactory.ParameterizedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/** The annotated type factory for the {@link ShrinkChecker}. */
public class ShrinkAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

  /** The erased {@code java.util.Map.Entry} type. */
  private final TypeMirror mapEntryErasure;

  /** The erased {@code java.util.Iterator} type. */
  private final TypeMirror iteratorErasure;

  // ── Hierarchy qualifiers ──────────

  /** The {@code @}{@link MaybeShrink} qualifier (top of Shrink hierarchy). */
  private final AnnotationMirror MAYBE_SHRINK;

  /** The {@code @}{@link Shrinkable} qualifier. */
  private final AnnotationMirror SHRINKABLE;

  /** The {@code @}{@link Unshrinkable} qualifier. */
  private final AnnotationMirror UNSHRINKABLE;

  /** The {@code @}{@link PolyShrink} qualifier. */
  private final AnnotationMirror POLY_SHRINK;

  /** The {@code @}{@link IteratorPolyMod} qualifier. */
  private final AnnotationMirror ITERATOR_PRESERVE_REMOVE;

  /**
   * Creates a ShrinkAnnotatedTypeFactory.
   *
   * @param checker the associated type-checker
   */
  @SuppressWarnings("this-escape")
  public ShrinkAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);
    // Cache type erasures.
    Types types = getProcessingEnv().getTypeUtils();
    this.mapEntryErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Map.Entry").asType());
    this.iteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Iterator").asType());

    // Initialize annotation mirrors after the hierarchy is established.
    this.MAYBE_SHRINK = AnnotationBuilder.fromClass(getElementUtils(), MaybeShrink.class);
    this.SHRINKABLE = AnnotationBuilder.fromClass(getElementUtils(), Shrinkable.class);
    this.UNSHRINKABLE = AnnotationBuilder.fromClass(getElementUtils(), Unshrinkable.class);
    this.POLY_SHRINK = AnnotationBuilder.fromClass(getElementUtils(), PolyShrink.class);
    this.ITERATOR_PRESERVE_REMOVE =
        AnnotationBuilder.fromClass(getElementUtils(), IteratorPolyMod.class);

    postInit();
  }

  @Override
  protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
    return new LinkedHashSet<>(
        Arrays.asList(
            MaybeShrink.class,
            Shrinkable.class,
            Unshrinkable.class,
            BottomShrink.class,
            PolyShrink.class,
            MaybeIteratorPolyMod.class,
            IteratorPolyMod.class));
  }

  /**
   * Expands whole-modifiability aliases into this hierarchy, with structural weakening only for
   * aliases whose meaning depends on the annotated type.
   *
   * <p>{@code @Modifiable} and {@code @Unmodifiable} claim all component capabilities, so on {@code
   * Map.Entry}, which cannot shrink, their shrink component canonicalizes to {@code @MaybeShrink}.
   * Explicit shrink qualifiers are left to normal canonicalization so users can still write and
   * preserve an explicit capability tuple such as {@code @Growable @Shrinkable @Replaceable Entry}.
   *
   * <p>{@code @PolyModifiable} is different: it should usually become {@code @PolyShrink}, but for
   * {@code Map.Entry} only the replace bit is meaningful to carry from the map receiver. Its shrink
   * bit is therefore {@code @MaybeShrink}.
   */
  @Override
  public AnnotationMirror canonicalAnnotation(
      AnnotationMirror annotation, @Nullable TypeMirror tm) {
    if (areSameByClass(annotation, Modifiable.class)) {
      return tm != null && typeCannotShrink(tm) ? MAYBE_SHRINK : SHRINKABLE;
    } else if (areSameByClass(annotation, Unmodifiable.class)) {
      return tm != null && typeCannotShrink(tm) ? MAYBE_SHRINK : UNSHRINKABLE;
    } else if (areSameByClass(annotation, MaybeModifiable.class)) {
      return MAYBE_SHRINK;
    } else if (areSameByClass(annotation, PolyModifiable.class)) {
      return tm != null && isMapEntry(tm) ? MAYBE_SHRINK : POLY_SHRINK;
    }
    return super.canonicalAnnotation(annotation);
  }

  @Override
  protected ParameterizedExecutableType methodFromUse(
      MethodInvocationTree tree, boolean inferTypeArgs) {
    ParameterizedExecutableType mType = super.methodFromUse(tree, inferTypeArgs);
    AnnotatedExecutableType method = mType.executableType();

    if (isIteratorMethod(tree, method)) {
      refineIteratorReturnType(tree, method);
    }

    if (ModifiabilityMethodUtils.isCollectionsPlumeWithoutDuplicates(tree)) {
      refineCollectionsPlumeWithoutDuplicatesReturnType(tree, method);
    }

    return mType;
  }

  /**
   * Refines the return type of {@code CollectionsPlume.withoutDuplicates(Collection)}.
   *
   * <p>{@code withoutDuplicates} has a conditional aliasing contract: it may return its argument
   * when the argument is already a List with no duplicates, but otherwise it returns a new
   * ArrayList. A stub annotation like
   *
   * <pre>{@code
   * static <T> @PolyShrink List<T> withoutDuplicates(@PolyShrink Collection<T> values)
   * }</pre>
   *
   * would be unsound, because an {@code @Unshrinkable} input could receive the fresh ArrayList,
   * whose static type should be {@code @Shrinkable}. It would also be too imprecise to always use
   * {@code @MaybeShrink}, because passing a {@code @Shrinkable} collection guarantees that both
   * possible results are shrinkable. Therefore, model the method here as preserving
   * {@code @Shrinkable} inputs and otherwise returning {@code @MaybeShrink}.
   *
   * @param tree the invocation of {@code withoutDuplicates}
   * @param methodType the annotated executable type of the invoked method
   */
  private void refineCollectionsPlumeWithoutDuplicatesReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror argumentType = getAnnotatedType(tree.getArguments().get(0));
    if (argumentType.hasPrimaryAnnotation(SHRINKABLE)) {
      methodType.getReturnType().replaceAnnotation(SHRINKABLE);
    }
    if (argumentType.hasPrimaryAnnotation(ITERATOR_PRESERVE_REMOVE)) {
      methodType.getReturnType().replaceAnnotation(ITERATOR_PRESERVE_REMOVE);
    }
  }

  /**
   * Refines {@code iterator()} return type based on {@code @IteratorPolyMod}.
   *
   * <p>If the receiver is {@code @Shrinkable} and either the receiver type use or the invoked
   * method receiver type has {@code @IteratorPolyMod}, then the result is {@code @Shrinkable
   * Iterator}. Otherwise, shrinkability precision is dropped to {@code @MaybeShrink}.
   *
   * @param tree the iterator method invocation
   * @param methodType the annotated executable type of the invoked method
   */
  private void refineIteratorReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror returnType = methodType.getReturnType();
    // Keep explicit unshrinkable/shrinkable iterator contracts (for example, CopyOnWriteArrayList,
    // ArrayList).
    if (returnType.hasPrimaryAnnotation(UNSHRINKABLE)
        || returnType.hasPrimaryAnnotation(SHRINKABLE)
        || returnType.hasPrimaryAnnotation(POLY_SHRINK)) {
      return;
    }

    Tree receiverTree = TreeUtils.getReceiverTree(tree);
    if (receiverTree == null) {
      return;
    }
    AnnotatedTypeMirror receiverType = getAnnotatedType(receiverTree);

    // all unshrinkable collections' iterators are unshrinkable.
    if (receiverType.hasPrimaryAnnotation(UNSHRINKABLE)) {
      returnType.replaceAnnotation(UNSHRINKABLE);
    }

    if (!receiverType.hasPrimaryAnnotation(SHRINKABLE)) {
      return;
    }

    // receiver type is @Shrinkable. check for @IteratorPolyMod
    if (hasIteratorPolyMod(receiverType)) {
      returnType.replaceAnnotation(SHRINKABLE);
    } else {
      returnType.replaceAnnotation(MAYBE_SHRINK);
    }
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
  private boolean isIteratorMethod(MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    ExecutableElement invokedMethod = TreeUtils.elementFromUse(tree);
    if (invokedMethod == null) {
      return false;
    }
    String methodName = invokedMethod.getSimpleName().toString();
    int argCount = tree.getArguments().size();
    if (!((methodName.equals("iterator") && argCount == 0)
        || (methodName.equals("listIterator") && argCount <= 1))) {
      return false;
    }

    TypeMirror returnUnderlying = methodType.getReturnType().getUnderlyingType();
    return TypesUtils.isErasedSubtype(returnUnderlying, iteratorErasure, types);
  }

  /**
   * Returns true if {@code type} has the {@code @IteratorPolyMod} marker annotation.
   *
   * @param type the type to test
   * @return true if {@code type} has the {@code @IteratorPolyMod} marker annotation
   */
  private boolean hasIteratorPolyMod(AnnotatedTypeMirror type) {
    if (type.hasPrimaryAnnotation(ITERATOR_PRESERVE_REMOVE)) {
      return true;
    }
    return AnnotationUtils.containsSameByClass(
        type.getUnderlyingType().getAnnotationMirrors(), IteratorPolyMod.class);
  }

  /**
   * Returns true if {@code type} structurally cannot support shrink operations.
   *
   * @param type the type to test
   * @return true if {@code type} structurally cannot support shrink operations
   */
  private boolean typeCannotShrink(TypeMirror type) {
    return isMapEntry(type);
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
