package org.checkerframework.checker.modifiability.seqgrow;

import com.sun.source.tree.MethodInvocationTree;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.checker.modifiability.ModifiabilityAnnotatedTypeFactory;
import org.checkerframework.checker.modifiability.qual.BottomSeqGrowable;
import org.checkerframework.checker.modifiability.qual.MaybeModifiable;
import org.checkerframework.checker.modifiability.qual.MaybeSeqGrowable;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.PolySeqGrowable;
import org.checkerframework.checker.modifiability.qual.PreservesModifiability;
import org.checkerframework.checker.modifiability.qual.SeqGrowable;
import org.checkerframework.checker.modifiability.qual.SeqUngrowable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/** The annotated type factory for the {@link SeqGrowChecker}. */
public class SeqGrowAnnotatedTypeFactory extends ModifiabilityAnnotatedTypeFactory {

  /** The erased {@code java.util.SequencedCollection} type. */
  private final @Nullable TypeMirror sequencedCollectionErasure;

  /** The {@code @}{@link MaybeSeqGrowable} qualifier. */
  private final AnnotationMirror MAYBE_SEQ_GROWABLE;

  /** The {@code @}{@link SeqGrowable} qualifier. */
  private final AnnotationMirror SEQ_GROWABLE;

  /** The {@code @}{@link SeqUngrowable} qualifier. */
  private final AnnotationMirror SEQ_UNGROWABLE;

  /** The {@code @}{@link PolySeqGrowable} qualifier. */
  private final AnnotationMirror POLY_SEQ_GROWABLE;

  /**
   * Creates a SeqGrowAnnotatedTypeFactory.
   *
   * @param checker the associated type-checker
   */
  @SuppressWarnings("this-escape")
  public SeqGrowAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);

    TypeElement sequencedCollectionElement =
        elements.getTypeElement("java.util.SequencedCollection");
    this.sequencedCollectionErasure =
        sequencedCollectionElement == null
            ? null
            : types.erasure(sequencedCollectionElement.asType());
    this.MAYBE_SEQ_GROWABLE = AnnotationBuilder.fromClass(elements, MaybeSeqGrowable.class);
    this.SEQ_GROWABLE = AnnotationBuilder.fromClass(elements, SeqGrowable.class);
    this.SEQ_UNGROWABLE = AnnotationBuilder.fromClass(elements, SeqUngrowable.class);
    this.POLY_SEQ_GROWABLE = AnnotationBuilder.fromClass(elements, PolySeqGrowable.class);

    postInit();
  }

  @Override
  protected AnnotationMirror maybeCapability() {
    return MAYBE_SEQ_GROWABLE;
  }

  @Override
  protected AnnotationMirror positiveCapability() {
    return SEQ_GROWABLE;
  }

  @Override
  protected AnnotationMirror negativeCapability() {
    return SEQ_UNGROWABLE;
  }

  @Override
  protected AnnotationMirror polyCapability() {
    return POLY_SEQ_GROWABLE;
  }

  @Override
  protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
    return new LinkedHashSet<>(
        Arrays.asList(
            MaybeSeqGrowable.class,
            SeqGrowable.class,
            SeqUngrowable.class,
            BottomSeqGrowable.class,
            PolySeqGrowable.class));
  }

  @Override
  public AnnotationMirror canonicalAnnotation(
      AnnotationMirror annotation, @Nullable TypeMirror tm) {
    if (tm != null) {
      if (areSameByClass(annotation, Modifiable.class)) {
        return typeCannotSeqGrow(tm) ? MAYBE_SEQ_GROWABLE : SEQ_GROWABLE;
      } else if (areSameByClass(annotation, Unmodifiable.class)) {
        return typeCannotSeqGrow(tm) ? MAYBE_SEQ_GROWABLE : SEQ_UNGROWABLE;
      }
    }
    if (areSameByClass(annotation, MaybeModifiable.class)
        || areSameByClass(annotation, UnmodifiableParam.class)) {
      return MAYBE_SEQ_GROWABLE;
    } else if (areSameByClass(annotation, PolyModifiable.class)) {
      return POLY_SEQ_GROWABLE;
    }
    return super.canonicalAnnotation(annotation);
  }

  @Override
  protected ParameterizedExecutableType methodFromUse(
      MethodInvocationTree tree, boolean inferTypeArgs) {
    ParameterizedExecutableType mType = super.methodFromUse(tree, inferTypeArgs);
    AnnotatedExecutableType method = mType.executableType();

    // if the method is annotated @PreservesModifiability
    ExecutableElement invokedMethod = TreeUtils.elementFromUse(tree);
    if (getDeclAnnotation(invokedMethod, PreservesModifiability.class) != null) {
      refinePreservesModifiabilityReturnType(tree, method);
    }

    return mType;
  }

  /**
   * Returns true if {@code type} structurally cannot support sequenced grow operations.
   *
   * <p>Only sequenced collections can support sequenced grow operations.
   *
   * @param type the type to test
   * @return true if {@code type} structurally cannot support sequenced grow operations
   */
  private boolean typeCannotSeqGrow(TypeMirror type) {
    return sequencedCollectionErasure == null
        || type.getKind() != TypeKind.DECLARED
        || !TypesUtils.isErasedSubtype(type, sequencedCollectionErasure, types);
  }
}
