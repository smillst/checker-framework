package org.checkerframework.checker.modifiability.shrink;

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
import org.checkerframework.checker.modifiability.qual.BottomShrinkable;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeIteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeModifiable;
import org.checkerframework.checker.modifiability.qual.MaybeShrinkable;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.PolyShrinkable;
import org.checkerframework.checker.modifiability.qual.PreservesModifiability;
import org.checkerframework.checker.modifiability.qual.Shrinkable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.checker.modifiability.qual.Unshrinkable;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/** The annotated type factory for the {@link ShrinkChecker}. */
public class ShrinkAnnotatedTypeFactory extends ModifiabilityAnnotatedTypeFactory {

  /** The erased {@code java.util.Map.Entry} type. */
  private final TypeMirror mapEntryErasure;

  /** The erased {@code java.util.Iterator} type. */
  private final TypeMirror iteratorErasure;

  // -- Hierarchy qualifiers ----------

  /** The {@code @}{@link MaybeShrinkable} qualifier. */
  private final AnnotationMirror MAYBE_SHRINKABLE;

  /** The {@code @}{@link Shrinkable} qualifier. */
  private final AnnotationMirror SHRINKABLE;

  /** The {@code @}{@link Unshrinkable} qualifier. */
  private final AnnotationMirror UNSHRINKABLE;

  /** The {@code @}{@link PolyShrinkable} qualifier. */
  private final AnnotationMirror POLY_SHRINKABLE;

  /**
   * Creates a ShrinkAnnotatedTypeFactory.
   *
   * @param checker the associated type-checker
   */
  @SuppressWarnings("this-escape")
  public ShrinkAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);

    Types types = getProcessingEnv().getTypeUtils();
    this.mapEntryErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Map.Entry").asType());
    this.iteratorErasure =
        types.erasure(getElementUtils().getTypeElement("java.util.Iterator").asType());
    // Initialize annotation mirrors after the hierarchy is established.
    this.MAYBE_SHRINKABLE = AnnotationBuilder.fromClass(getElementUtils(), MaybeShrinkable.class);
    this.SHRINKABLE = AnnotationBuilder.fromClass(getElementUtils(), Shrinkable.class);
    this.UNSHRINKABLE = AnnotationBuilder.fromClass(getElementUtils(), Unshrinkable.class);
    this.POLY_SHRINKABLE = AnnotationBuilder.fromClass(getElementUtils(), PolyShrinkable.class);

    postInit();
  }

  @Override
  protected AnnotationMirror maybeCapability() {
    return MAYBE_SHRINKABLE;
  }

  @Override
  protected AnnotationMirror positiveCapability() {
    return SHRINKABLE;
  }

  @Override
  protected AnnotationMirror negativeCapability() {
    return UNSHRINKABLE;
  }

  @Override
  protected AnnotationMirror polyCapability() {
    return POLY_SHRINKABLE;
  }

  @Override
  protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
    return new LinkedHashSet<>(
        Arrays.asList(
            MaybeShrinkable.class,
            Shrinkable.class,
            Unshrinkable.class,
            BottomShrinkable.class,
            PolyShrinkable.class,
            MaybeIteratorPolyMod.class,
            IteratorPolyMod.class));
  }

  /**
   * Expands whole-modifiability aliases into this hierarchy, with structural weakening only for
   * aliases whose meaning depends on the annotated type.
   *
   * <p>{@code @Modifiable} and {@code @Unmodifiable} claim all component capabilities, so on {@code
   * Map.Entry}, which cannot shrink, their shrink component canonicalizes to
   * {@code @MaybeShrinkable}.
   *
   * <p>{@code @PolyModifiable} is different: it should usually become {@code @PolyShrinkable}, but
   * for {@code Map.Entry} only the replace bit is meaningful to carry from the map receiver. Its
   * shrink bit is therefore {@code @MaybeShrinkable}.
   */
  @Override
  public AnnotationMirror canonicalAnnotation(
      AnnotationMirror annotation, @Nullable TypeMirror tm) {
    if (tm != null) {
      if (areSameByClass(annotation, Modifiable.class)) {
        return typeCannotShrink(tm) ? MAYBE_SHRINKABLE : SHRINKABLE;
      } else if (areSameByClass(annotation, Unmodifiable.class)) {
        return typeCannotShrink(tm) ? MAYBE_SHRINKABLE : UNSHRINKABLE;
      } else if (areSameByClass(annotation, PolyModifiable.class)) {
        return isMapEntry(tm) ? MAYBE_SHRINKABLE : POLY_SHRINKABLE;
      }
    }
    if (areSameByClass(annotation, MaybeModifiable.class)
        || areSameByClass(annotation, UnmodifiableParam.class)) {
      return MAYBE_SHRINKABLE;
    }
    return super.canonicalAnnotation(annotation);
  }

  @Override
  protected ParameterizedExecutableType methodFromUse(
      MethodInvocationTree tree, boolean inferTypeArgs) {
    ParameterizedExecutableType mType = super.methodFromUse(tree, inferTypeArgs);
    AnnotatedExecutableType method = mType.executableType();

    // if the method is iterator() (including listIterator()).
    TypeMirror returnUnderlying = method.getReturnType().getUnderlyingType();
    if (TypesUtils.isErasedSubtype(returnUnderlying, iteratorErasure, types)) {
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
