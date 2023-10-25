package org.checkerframework.framework.util.typeinference8.types;

import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.framework.util.typeinference8.util.Java8InferenceContext;

public class Qualifier extends AbstractQualifier {

  private final AnnotationMirror annotation;

  Qualifier(AnnotationMirror annotation, Java8InferenceContext context) {
    super(annotation, context);
    this.annotation = annotation;
  }

  public AnnotationMirror getAnnotation() {
    return annotation;
  }

  @Override
  public AnnotationMirror resolve() {
    return annotation;
  }
}
