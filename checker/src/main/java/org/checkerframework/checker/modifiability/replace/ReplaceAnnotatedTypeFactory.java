package org.checkerframework.checker.modifiability.replace;

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
import org.checkerframework.checker.modifiability.qual.BottomReplace;
import org.checkerframework.checker.modifiability.qual.IteratorPolyShrink;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.PolyReplace;
import org.checkerframework.checker.modifiability.qual.Replaceable;
import org.checkerframework.checker.modifiability.qual.UnknownIteratorPolyShrink;
import org.checkerframework.checker.modifiability.qual.UnknownModifiability;
import org.checkerframework.checker.modifiability.qual.UnknownReplace;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.checker.modifiability.qual.Unreplaceable;
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

  // ── Hierarchy qualifiers ──────────

  /** The {@code @}{@link UnknownReplace} qualifier (top of Replace hierarchy). */
  private final AnnotationMirror UNKNOWN_REPLACE;

  /** The {@code @}{@link Replaceable} qualifier. */
  private final AnnotationMirror REPLACEABLE;

  /** The {@code @}{@link Unreplaceable} qualifier. */
  private final AnnotationMirror UNREPLACEABLE;

  /** The {@code @}{@link PolyReplace} qualifier. */
  private final AnnotationMirror POLY_REPLACE;

  /** The {@code @}{@link UnknownIteratorPolyShrink} qualifier. */
  private final AnnotationMirror UNKNOWN_ITER;

  /** The {@code @}{@link IteratorPolyShrink} qualifier. */
  private final AnnotationMirror ITERATOR_PRESERVE_REMOVE;

  /**
   * Creates a ReplaceAnnotatedTypeFactory.
   *
   * @param checker the associated type-checker
   */
  @SuppressWarnings("this-escape")
  public ReplaceAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);
    // Cache type erasures.
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
    this.UNKNOWN_REPLACE = AnnotationBuilder.fromClass(getElementUtils(), UnknownReplace.class);
    this.REPLACEABLE = AnnotationBuilder.fromClass(getElementUtils(), Replaceable.class);
    this.UNREPLACEABLE = AnnotationBuilder.fromClass(getElementUtils(), Unreplaceable.class);
    this.POLY_REPLACE = AnnotationBuilder.fromClass(getElementUtils(), PolyReplace.class);
    this.UNKNOWN_ITER =
        AnnotationBuilder.fromClass(getElementUtils(), UnknownIteratorPolyShrink.class);
    this.ITERATOR_PRESERVE_REMOVE =
        AnnotationBuilder.fromClass(getElementUtils(), IteratorPolyShrink.class);

    addAliasedTypeAnnotation(Modifiable.class, REPLACEABLE);
    addAliasedTypeAnnotation(Unmodifiable.class, UNREPLACEABLE);
    addAliasedTypeAnnotation(UnknownModifiability.class, UNKNOWN_REPLACE);
    addAliasedTypeAnnotation(PolyModifiable.class, POLY_REPLACE);
    postInit();
  }

  @Override
  protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
    return new LinkedHashSet<>(
        Arrays.asList(
            UnknownReplace.class,
            Replaceable.class,
            Unreplaceable.class,
            BottomReplace.class,
            PolyReplace.class,
            UnknownIteratorPolyShrink.class,
            IteratorPolyShrink.class));
  }

  @Override
  protected TypeAnnotator createTypeAnnotator() {
    return new ListTypeAnnotator(new ReplaceTypeAnnotator(this), super.createTypeAnnotator());
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
   * <p>{@code withoutDuplicates} has a conditional aliasing contract: it may return its argument
   * when the argument is already a List with no duplicates, but otherwise it returns a new
   * ArrayList. A stub annotation like
   *
   * <pre>{@code
   * static <T> @PolyReplace List<T> withoutDuplicates(@PolyReplace Collection<T> values)
   * }</pre>
   *
   * would be unsound, because an {@code @Unreplaceable} input could receive the fresh ArrayList,
   * whose static type should be {@code @Replaceable}. It would also be too imprecise to always use
   * {@code @UnknownReplace}, because passing a {@code @Replaceable} collection guarantees that both
   * possible results are replaceable. Therefore, model the method here as preserving
   * {@code @Replaceable} inputs and otherwise returning {@code @UnknownReplace}.
   *
   * @param tree the invocation of {@code withoutDuplicates}
   * @param methodType the annotated executable type of the invoked method
   */
  private void refineCollectionsPlumeWithoutDuplicatesReturnType(
      MethodInvocationTree tree, AnnotatedExecutableType methodType) {
    AnnotatedTypeMirror argumentType = getAnnotatedType(tree.getArguments().get(0));
    if (argumentType.hasPrimaryAnnotation(REPLACEABLE)) {
      methodType.getReturnType().replaceAnnotation(REPLACEABLE);
    } else {
      methodType.getReturnType().replaceAnnotation(UNKNOWN_REPLACE);
    }
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
    if (returnType.hasPrimaryAnnotation(UNREPLACEABLE)
        || returnType.hasPrimaryAnnotation(REPLACEABLE)
        || returnType.hasPrimaryAnnotation(POLY_REPLACE)) {
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

    if (!receiverType.hasPrimaryAnnotation(REPLACEABLE)) {
      return;
    }

    if (hasIteratorPolyShrink(receiverType)) {
      returnType.replaceAnnotation(REPLACEABLE);
    } else {
      returnType.replaceAnnotation(UNKNOWN_REPLACE);
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
   * Removes capabilities that cannot be supported by structural constraints of the collection type:
   *
   * <ul>
   *   <li>Collection itself (not subtypes): remove Replace capability → set Replace to
   *       {@code @UnknownReplace}
   *   <li>Set or Queue (not LinkedList): remove Replace capability → set Replace to
   *       {@code @UnknownReplace}
   *   <li>Iterator: remove Replace capabilities
   * </ul>
   */
  private class ReplaceTypeAnnotator extends TypeAnnotator {
    /**
     * Creates a new ReplaceTypeAnnotator.
     *
     * @param factory the associated type factory
     */
    public ReplaceTypeAnnotator(ReplaceAnnotatedTypeFactory factory) {
      super(factory);
    }

    @Override
    public Void visitDeclared(AnnotatedDeclaredType type, Void p) {
      super.visitDeclared(type, p);

      // Skip structural refinement for polymorphic types.
      if (type.hasPrimaryAnnotation(POLY_REPLACE)) {
        return null;
      }

      TypeMirror underlyingType = type.getUnderlyingType();
      TypeMirror erasedUnderlyingType = types.erasure(underlyingType);

      if (types.isSameType(erasedUnderlyingType, collectionErasure)) {
        // Collection itself: Drop R bit
        type.replaceAnnotation(UNKNOWN_REPLACE);
      } else if (TypesUtils.isErasedSubtype(underlyingType, setErasure, types)) {
        // Set: Drop R bit
        type.replaceAnnotation(UNKNOWN_REPLACE);
      } else if (TypesUtils.isErasedSubtype(underlyingType, queueErasure, types)
          && !TypesUtils.isErasedSubtype(underlyingType, linkedListErasure, types)) {
        // Queue (but not LinkedList): Drop R bit
        type.replaceAnnotation(UNKNOWN_REPLACE);
      } else if (TypesUtils.isErasedSubtype(underlyingType, iteratorErasure, types)
          && !TypesUtils.isErasedSubtype(underlyingType, listIteratorErasure, types)) {
        // Iterator: Drop R bits
        type.replaceAnnotation(UNKNOWN_REPLACE);
      }
      return null;
    }
  }

  @Override
  protected QualifierUpperBounds createQualifierUpperBounds() {
    return new QualifierUpperBounds(this) {
      private final AnnotationMirrorSet unknownReplaceAndIter =
          AnnotationMirrorSet.unmodifiableSet(Arrays.asList(UNKNOWN_REPLACE, UNKNOWN_ITER));

      @Override
      public AnnotationMirrorSet getBoundQualifiers(TypeMirror type) {
        TypeMirror erasedType = types.erasure(type);
        if (types.isSameType(erasedType, collectionErasure)) {
          // Elements of a raw Collection are treated as @UnknownReplace.
          return unknownReplaceAndIter;
        } else if (TypesUtils.isErasedSubtype(type, setErasure, types)) {
          // Elements of a set can never be replaced, so treat them as @UnknownReplace. Even if
          // they are annotation @Modifiable in a stubfile.
          return unknownReplaceAndIter;
        } else if (TypesUtils.isErasedSubtype(type, queueErasure, types)
            && !TypesUtils.isErasedSubtype(type, linkedListErasure, types)) {
          // Elements of a queue (but not LinkedList) can never be replaced, so treat them as
          // @UnknownReplace.
          return unknownReplaceAndIter;
        } else if (TypesUtils.isErasedSubtype(type, iteratorErasure, types)
            && !TypesUtils.isErasedSubtype(type, listIteratorErasure, types)) {
          // Iterators cannot replace elements.
          return unknownReplaceAndIter;
        }
        AnnotationMirrorSet bounds = new AnnotationMirrorSet(super.getBoundQualifiers(type));
        bounds.add(UNKNOWN_ITER);
        return bounds;
      }
    };
  }
}
