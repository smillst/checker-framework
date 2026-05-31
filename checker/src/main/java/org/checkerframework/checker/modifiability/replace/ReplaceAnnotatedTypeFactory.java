package org.checkerframework.checker.modifiability.replace;

import com.sun.source.tree.MethodInvocationTree;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.checkerframework.checker.modifiability.ModifiabilityAnnotatedTypeFactory;
import org.checkerframework.checker.modifiability.qual.BottomReplaceable;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeIteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeModifiable;
import org.checkerframework.checker.modifiability.qual.MaybeReplaceable;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.PolyReplaceable;
import org.checkerframework.checker.modifiability.qual.PreservesModifiability;
import org.checkerframework.checker.modifiability.qual.Replaceable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.checker.modifiability.qual.Unreplaceable;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeFactory.ParameterizedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/** The annotated type factory for the {@link ReplaceChecker}. */
public class ReplaceAnnotatedTypeFactory extends ModifiabilityAnnotatedTypeFactory {

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

    postInit();
  }

  @Override
  protected AnnotationMirror maybeCapability() {
    return MAYBE_REPLACEABLE;
  }

  @Override
  protected AnnotationMirror positiveCapability() {
    return REPLACEABLE;
  }

  @Override
  protected AnnotationMirror negativeCapability() {
    return UNREPLACEABLE;
  }

  @Override
  protected AnnotationMirror polyCapability() {
    return POLY_REPLACEABLE;
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
    }
    if (areSameByClass(annotation, MaybeModifiable.class)
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

    // if the method is listIterator().
    TypeMirror returnUnderlying = method.getReturnType().getUnderlyingType();
    if (TypesUtils.isErasedSubtype(returnUnderlying, listIteratorErasure, types)) {
      refineIteratorReturnType(tree, method);
    }

    // if the method is annotated @PreservesModifiability
    ExecutableElement invokedMethod = TreeUtils.elementFromUse(tree);
    if (getDeclAnnotation(invokedMethod, PreservesModifiability.class) != null) {
      refinePreservesModifiabilityReturnType(tree, method);
    }

    return mType;
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
