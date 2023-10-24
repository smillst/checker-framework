package org.checkerframework.framework.util.typeinference8.types;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.framework.util.typeinference8.util.Java8InferenceContext;
import org.checkerframework.javacutil.AnnotationMirrorMap;
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

  Set<AnnotationMirror> lub(Set<AbstractQualifier> quals) {
    Map<String, AbstractQualifier> m;

    for(AbstractQualifier qual:quals){

    }

  }

  public static Set<AbstractQualifier> create(Set<AnnotationMirror> annos, AnnotationMirrorMap<QualifierVar> qualifierVars, Java8InferenceContext context) {
    if(qualifierVars.isEmpty()) {
      return create(annos, context);
    }

    Set<AbstractQualifier> quals = new HashSet<>();
    for(AnnotationMirror anno: annos){
      if(qualifierVars.containsKey(anno)) {
        quals.add(qualifierVars.get(anno));
      } else {
        quals.add(new Qualifier(anno, context));
      }
    }
    return quals;
  }
  public static Set<AbstractQualifier> create(Set<AnnotationMirror> annos, Java8InferenceContext context) {
    Set<AbstractQualifier> quals = new HashSet<>();
    for(AnnotationMirror anno:annos){
      quals.add(new Qualifier(anno, context));
    }
    return quals;
  }
}
