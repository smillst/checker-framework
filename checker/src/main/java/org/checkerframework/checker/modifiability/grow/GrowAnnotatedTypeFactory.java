package org.checkerframework.checker.modifiability.grow;

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
import org.checkerframework.checker.modifiability.qual.BottomGrowable;
import org.checkerframework.checker.modifiability.qual.Growable;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeGrowable;
import org.checkerframework.checker.modifiability.qual.MaybeIteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeModifiable;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyGrowable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.PreservesModifiability;
import org.checkerframework.checker.modifiability.qual.Ungrowable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/** The annotated type factory for the {@link GrowChecker}. */
public class GrowAnnotatedTypeFactory extends ModifiabilityAnnotatedTypeFactory {

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

    postInit();
  }

  @Override
  protected AnnotationMirror maybeCapability() {
    return MAYBE_GROWABLE;
  }

  @Override
  protected AnnotationMirror positiveCapability() {
    return GROWABLE;
  }

  @Override
  protected AnnotationMirror negativeCapability() {
    return UNGROWABLE;
  }

  @Override
  protected AnnotationMirror polyCapability() {
    return POLY_GROWABLE;
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
            MaybeIteratorPolyMod.class,
            IteratorPolyMod.class));
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
    }
    if (areSameByClass(annotation, MaybeModifiable.class)
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
      refineIteratorReturnType(tree, method);
    }

    ExecutableElement invokedMethod = TreeUtils.elementFromUse(tree);
    if (getDeclAnnotation(invokedMethod, PreservesModifiability.class) != null) {
      refinePreservesModifiabilityReturnType(tree, method);
    }

    return mType;
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
    return TypesUtils.isErasedSubtype(type, mapEntryErasure, types);
  }
}
