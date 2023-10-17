package org.checkerframework.framework.util.typeinference8.types;

import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.framework.util.typeinference8.util.Java8InferenceContext;
import org.checkerframework.javacutil.AnnotationUtils;

public class AbstractQualifier {
  final @Interned String hierarchyName;
  AbstractQualifier(AnnotationMirror anno, Java8InferenceContext context) {
    AnnotationMirror top = context.typeFactory.getQualifierHierarchy().getTopAnnotation(anno);
    hierarchyName = AnnotationUtils.annotationNameInterned(top);
  }

  public boolean sameHierarchy(AbstractQualifier t) {
    return hierarchyName == t.hierarchyName;
  }

  public static Set<AbstractQualifier> create(AbstractType type) {
    Set<AbstractQualifier> quals = new HashSet<>();


  }

  public static AbstractQualifier create(AnnotationMirror anno) {


  }
}
