package org.checkerframework.framework.util.typeinference8.types;

import javax.lang.model.element.AnnotationMirror;

public class Qualifier extends AbstractQualifier {

  private final AnnotationMirror annotation;

  Qualifier(AnnotationMirror annotation) {
    this.annotation = annotation;
  }

  public AnnotationMirror getAnnotation() {
    return annotation;
  }
}
