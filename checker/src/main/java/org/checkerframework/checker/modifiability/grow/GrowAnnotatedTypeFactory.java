package org.checkerframework.checker.modifiability.grow;

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
import org.checkerframework.checker.modifiability.qual.BottomGrow;
import org.checkerframework.checker.modifiability.qual.Growable;
import org.checkerframework.checker.modifiability.qual.IteratorPolyShrink;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyGrow;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.Ungrowable;
import org.checkerframework.checker.modifiability.qual.UnknownGrow;
import org.checkerframework.checker.modifiability.qual.UnknownIteratorPolyShrink;
import org.checkerframework.checker.modifiability.qual.UnknownModifiability;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
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

/** The annotated type factory for the {@link GrowChecker}. */
public class GrowAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

  /** The erased {@code java.util.Map.Entry} type. */
  private final TypeMirror mapEntryErasure;

  /** The erased {@code java.util.Iterator} type. */
  private final TypeMirror iteratorErasure;

  /** The erased {@code java.util.ListIterator} type. */
  private final TypeMirror listIteratorErasure;

  // ── Hierarchy qualifiers ──────────

  /** The {@code @}{@link UnknownGrow} qualifier (top of Grow hierarchy). */
  private final AnnotationMirror UNKNOWN_GROW;

  /** The {@code @}{@link Growable} qualifier. */
  private final AnnotationMirror GROWABLE;

  /** The {@code @}{@link Ungrowable} qualifier. */
  private final AnnotationMirror UNGROWABLE;

  /** The {@code @}{@link PolyGrow} qualifier. */
  private final AnnotationMirror POLY_GROW;

  /** The {@code @}{@link UnknownIteratorPolyShrink} qualifier. */
  private final AnnotationMirror UNKNOWN_ITER;

  /** The {@code @}{@link IteratorPolyShrink} qualifier. */
  private final AnnotationMirror ITERATOR_PRESERVE_REMOVE;

  /**
   * Creates a GrowAnnotatedTypeFactory.
   *
   * @param checker the associated type-checker
   */
  @SuppressWarnings("this-escape")
  public GrowAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);
    // Cache type erasures.
    Types types = getProcessingEnv().getTypeUtils();
    this.mapEntryErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Map.Entry").asType());
    this.iteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Iterator").asType());
    this.listIteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.ListIterator").asType());
    // Initialize annotation mirrors after the hierarchy is established.
    this.UNKNOWN_GROW = AnnotationBuilder.fromClass(getElementUtils(), UnknownGrow.class);
    this.GROWABLE = AnnotationBuilder.fromClass(getElementUtils(), Growable.class);
    this.UNGROWABLE = AnnotationBuilder.fromClass(getElementUtils(), Ungrowable.class);
    this.POLY_GROW = AnnotationBuilder.fromClass(getElementUtils(), PolyGrow.class);
    this.UNKNOWN_ITER =
        AnnotationBuilder.fromClass(getElementUtils(), UnknownIteratorPolyShrink.class);
    this.ITERATOR_PRESERVE_REMOVE =
        AnnotationBuilder.fromClass(getElementUtils(), IteratorPolyShrink.class);

    addAliasedTypeAnnotation(Modifiable.class, GROWABLE);
    addAliasedTypeAnnotation(Unmodifiable.class, UNGROWABLE);
    addAliasedTypeAnnotation(UnknownModifiability.class, UNKNOWN_GROW);
    addAliasedTypeAnnotation(PolyModifiable.class, POLY_GROW);
    postInit();
  }

  @Override
  protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
    return new LinkedHashSet<>(
        Arrays.asList(
            UnknownGrow.class,
            Growable.class,
            Ungrowable.class,
            BottomGrow.class,
            PolyGrow.class,
            UnknownIteratorPolyShrink.class,
            IteratorPolyShrink.class));
  }

  @Override
  protected TypeAnnotator createTypeAnnotator() {
    return new ListTypeAnnotator(new GrowTypeAnnotator(this), super.createTypeAnnotator());
  }

  @Override
  protected ParameterizedExecutableType methodFromUse(
      MethodInvocationTree tree, boolean inferTypeArgs) {
    ParameterizedExecutableType mType = super.methodFromUse(tree, inferTypeArgs);
    AnnotatedExecutableType method = mType.executableType();

    if (isListIteratorMethod(tree, method)) {
      refineListIteratorReturnType(tree, method);
    }

    if (!ModifiabilityMethodUtils.isCollectionsPlumeWithoutDuplicates(tree)) {
      return mType;
    }

    AnnotatedTypeMirror argumentType = getAnnotatedType(tree.getArguments().get(0));
    if (argumentType.hasPrimaryAnnotation(GROWABLE)) {
      method.getReturnType().replaceAnnotation(GROWABLE);
    } else {
      method.getReturnType().replaceAnnotation(UNKNOWN_GROW);
    }
    return mType;
  }

  /**
   * Refines {@code listIterator()} return type based on {@code @IteratorPolyShrink}.
   *
   * @param tree the listIterator method invocation
   * @param methodType the annotated executable type of the invoked method
   */
  private void refineListIteratorReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror returnType = methodType.getReturnType();
    if (returnType.hasPrimaryAnnotation(UNGROWABLE)
        || returnType.hasPrimaryAnnotation(GROWABLE)
        || returnType.hasPrimaryAnnotation(POLY_GROW)) {
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

    if (hasIteratorPolyShrink(receiverType)) {
      returnType.replaceAnnotation(GROWABLE);
    } else {
      returnType.replaceAnnotation(UNKNOWN_GROW);
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
   * Removes grow capability for types that structurally cannot support it:
   *
   * <ul>
   *   <li>Set or Queue (not LinkedList): remove Replace capability → set Replace to
   *       {@code @UnknownReplace}
   *   <li>Map.Entry: remove Grow and Shrink capabilities
   *   <li>Iterator: remove Grow and Replace capabilities
   * </ul>
   */
  private class GrowTypeAnnotator extends TypeAnnotator {
    /**
     * Creates a new GrowTypeAnnotator.
     *
     * @param factory the associated type factory
     */
    public GrowTypeAnnotator(GrowAnnotatedTypeFactory factory) {
      super(factory);
    }

    @Override
    public Void visitDeclared(AnnotatedDeclaredType type, Void p) {
      super.visitDeclared(type, p);

      // Skip structural refinement for polymorphic types.
      if (type.hasPrimaryAnnotation(POLY_GROW)) {
        return null;
      }

      TypeMirror underlyingType = type.getUnderlyingType();

      if (TypesUtils.isErasedSubtype(underlyingType, mapEntryErasure, types)) {
        // Map.Entry: no grow.
        type.replaceAnnotation(UNKNOWN_GROW);
      } else if (TypesUtils.isErasedSubtype(underlyingType, iteratorErasure, types)
          && !TypesUtils.isErasedSubtype(underlyingType, listIteratorErasure, types)) {
        // Iterator: no grow.
        type.replaceAnnotation(UNKNOWN_GROW);
      }

      return null;
    }
  }

  @Override
  protected QualifierUpperBounds createQualifierUpperBounds() {
    return new QualifierUpperBounds(this) {
      private final AnnotationMirrorSet unknownGrowAndIter =
          AnnotationMirrorSet.unmodifiableSet(Arrays.asList(UNKNOWN_GROW, UNKNOWN_ITER));

      @Override
      public AnnotationMirrorSet getBoundQualifiers(TypeMirror type) {
        if (TypesUtils.isErasedSubtype(type, mapEntryErasure, types)) {
          // Map.Entry cannot be grown.
        } else if (TypesUtils.isErasedSubtype(type, iteratorErasure, types)
            && !TypesUtils.isErasedSubtype(type, listIteratorErasure, types)) {
          // Standard Iterator (not ListIterator) cannot be grown.
          return unknownGrowAndIter;
        }
        AnnotationMirrorSet bounds = new AnnotationMirrorSet(super.getBoundQualifiers(type));
        bounds.add(UNKNOWN_ITER);
        return bounds;
      }
    };
  }
}
