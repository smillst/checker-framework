package org.checkerframework.checker.modifiability.shrink;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.checkerframework.checker.modifiability.ModifiabilityMethodUtils;
import org.checkerframework.checker.modifiability.qual.BottomShrink;
import org.checkerframework.checker.modifiability.qual.IteratorPolyShrink;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.PolyShrink;
import org.checkerframework.checker.modifiability.qual.Shrinkable;
import org.checkerframework.checker.modifiability.qual.UnknownIteratorPolyShrink;
import org.checkerframework.checker.modifiability.qual.UnknownModifiability;
import org.checkerframework.checker.modifiability.qual.UnknownShrink;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.checker.modifiability.qual.Unshrinkable;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeFactory.ParameterizedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.QualifierUpperBounds;
import org.checkerframework.framework.type.typeannotator.ListTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.TypeAnnotator;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationMirrorSet;
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

  /** The {@code @}{@link UnknownShrink} qualifier (top of Shrink hierarchy). */
  private final AnnotationMirror UNKNOWN_SHRINK;

  /** The {@code @}{@link Shrinkable} qualifier. */
  private final AnnotationMirror SHRINKABLE;

  /** The {@code @}{@link Unshrinkable} qualifier. */
  private final AnnotationMirror UNSHRINKABLE;

  /** The {@code @}{@link PolyShrink} qualifier. */
  private final AnnotationMirror POLY_SHRINK;

  /**
   * The {@code @}{@link UnknownIteratorPolyShrink} qualifier (top of iterator-preservation
   * hierarchy).
   */
  private final AnnotationMirror UNKNOWN_ITER;

  /** The {@code @}{@link IteratorPolyShrink} qualifier. */
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
    this.UNKNOWN_SHRINK = AnnotationBuilder.fromClass(getElementUtils(), UnknownShrink.class);
    this.SHRINKABLE = AnnotationBuilder.fromClass(getElementUtils(), Shrinkable.class);
    this.UNSHRINKABLE = AnnotationBuilder.fromClass(getElementUtils(), Unshrinkable.class);
    this.POLY_SHRINK = AnnotationBuilder.fromClass(getElementUtils(), PolyShrink.class);
    this.UNKNOWN_ITER =
        AnnotationBuilder.fromClass(getElementUtils(), UnknownIteratorPolyShrink.class);
    this.ITERATOR_PRESERVE_REMOVE =
        AnnotationBuilder.fromClass(getElementUtils(), IteratorPolyShrink.class);

    addAliasedTypeAnnotation(Modifiable.class, SHRINKABLE);
    addAliasedTypeAnnotation(Unmodifiable.class, UNSHRINKABLE);
    addAliasedTypeAnnotation(UnknownModifiability.class, UNKNOWN_SHRINK);
    addAliasedTypeAnnotation(PolyModifiable.class, POLY_SHRINK);
    postInit();
  }

  @Override
  protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
    return new LinkedHashSet<>(
        Arrays.asList(
            UnknownShrink.class,
            Shrinkable.class,
            Unshrinkable.class,
            BottomShrink.class,
            PolyShrink.class,
            UnknownIteratorPolyShrink.class,
            IteratorPolyShrink.class));
  }

  @Override
  protected TypeAnnotator createTypeAnnotator() {
    return new ListTypeAnnotator(new ShrinkTypeAnnotator(this), super.createTypeAnnotator());
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
   * {@code @UnknownShrink}, because passing a {@code @Shrinkable} collection guarantees that both
   * possible results are shrinkable. Therefore, model the method here as preserving
   * {@code @Shrinkable} inputs and otherwise returning {@code @UnknownShrink}.
   *
   * @param tree the invocation of {@code withoutDuplicates}
   * @param methodType the annotated executable type of the invoked method
   */
  private void refineCollectionsPlumeWithoutDuplicatesReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror argumentType = getAnnotatedType(tree.getArguments().get(0));
    AnnotationMirror returnAnnotation =
        argumentType.hasPrimaryAnnotation(SHRINKABLE) ? SHRINKABLE : UNKNOWN_SHRINK;
    methodType.getReturnType().replaceAnnotation(returnAnnotation);
  }

  /**
   * Refines {@code iterator()} return type based on {@code @IteratorPolyShrink}.
   *
   * <p>If the receiver is {@code @Shrinkable} and either the receiver type use or the invoked
   * method receiver type has {@code @IteratorPolyShrink}, then the result is {@code @Shrinkable
   * Iterator}. Otherwise, shrinkability precision is dropped to {@code @UnknownShrink}.
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

    // receiver type is @Shrinkable. check for @IteratorPolyShrink
    if (hasIteratorPolyShrink(receiverType)) {
      returnType.replaceAnnotation(SHRINKABLE);
    } else {
      returnType.replaceAnnotation(UNKNOWN_SHRINK);
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
   * Returns true if {@code type} has the {@code @IteratorPolyShrink} marker annotation.
   *
   * @param type the type to test
   * @return true if {@code type} has the {@code @IteratorPolyShrink} marker annotation
   */
  private boolean hasIteratorPolyShrink(AnnotatedTypeMirror type) {
    if (type.hasPrimaryAnnotation(ITERATOR_PRESERVE_REMOVE)) {
      return true;
    }
    return AnnotationUtils.containsSameByClass(
        type.getUnderlyingType().getAnnotationMirrors(), IteratorPolyShrink.class);
  }

  /**
   * Removes shrink capability for types that structurally cannot support it:
   *
   * <ul>
   *   <li>Map.Entry: cannot shrink, set to {@code @UnknownShrink}
   * </ul>
   */
  private class ShrinkTypeAnnotator extends TypeAnnotator {

    /**
     * Creates a new ShrinkTypeAnnotator.
     *
     * @param factory the associated type factory
     */
    public ShrinkTypeAnnotator(ShrinkAnnotatedTypeFactory factory) {
      super(factory);
    }

    @Override
    public Void visitDeclared(AnnotatedDeclaredType type, Void p) {
      super.visitDeclared(type, p);

      // Skip structural refinement for polymorphic types.
      if (type.hasPrimaryAnnotation(POLY_SHRINK)) {
        return null;
      }

      TypeMirror underlyingType = type.getUnderlyingType();

      if (TypesUtils.isErasedSubtype(underlyingType, mapEntryErasure, types)) {
        // Map.Entry: no shrink.
        type.replaceAnnotation(UNKNOWN_SHRINK);
      }

      return null;
    }
  }

  @Override
  protected QualifierUpperBounds createQualifierUpperBounds() {
    return new QualifierUpperBounds(this) {
      @Override
      public AnnotationMirrorSet getBoundQualifiers(TypeMirror type) {
        if (TypesUtils.isErasedSubtype(type, mapEntryErasure, types)) {
          // Map.Entry uses fixed upper bounds in both supported hierarchies.
          AnnotationMirrorSet bounds = new AnnotationMirrorSet();
          bounds.add(UNKNOWN_SHRINK);
          bounds.add(UNKNOWN_ITER);
          return bounds;
        }
        return super.getBoundQualifiers(type);
      }
    };
  }
}
