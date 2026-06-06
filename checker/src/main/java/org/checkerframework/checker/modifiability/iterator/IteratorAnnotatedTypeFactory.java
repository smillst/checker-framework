package org.checkerframework.checker.modifiability.iterator;

import com.sun.source.tree.MethodInvocationTree;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import org.checkerframework.checker.modifiability.ModifiabilityAnnotatedTypeFactory;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeIteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.PolyIteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.PreservesModifiability;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.TreeUtils;

/** The annotated type factory for the {@link IteratorChecker}. */
public class IteratorAnnotatedTypeFactory extends ModifiabilityAnnotatedTypeFactory {

  /** The {@code @}{@link IteratorPolyMod} qualifier. */
  private final AnnotationMirror ITERATOR_POLY_MOD;

  /** The {@code @}{@link PolyIteratorPolyMod} qualifier. */
  private final AnnotationMirror POLY_ITERATOR_POLY_MOD;

  /**
   * Creates an IteratorAnnotatedTypeFactory.
   *
   * @param checker the associated type-checker
   */
  @SuppressWarnings("this-escape")
  public IteratorAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);
    this.ITERATOR_POLY_MOD = AnnotationBuilder.fromClass(elements, IteratorPolyMod.class);
    this.POLY_ITERATOR_POLY_MOD = AnnotationBuilder.fromClass(elements, PolyIteratorPolyMod.class);
    postInit();
  }

  @Override
  protected AnnotationMirror positiveCapability() {
    return ITERATOR_POLY_MOD;
  }

  @Override
  protected AnnotationMirror negativeCapability() {
    throw new UnsupportedOperationException("The iterator hierarchy has no negative qualifier.");
  }

  @Override
  protected AnnotationMirror polyCapability() {
    return POLY_ITERATOR_POLY_MOD;
  }

  @Override
  protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
    return new LinkedHashSet<>(
        Arrays.asList(
            MaybeIteratorPolyMod.class, IteratorPolyMod.class, PolyIteratorPolyMod.class));
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
}
