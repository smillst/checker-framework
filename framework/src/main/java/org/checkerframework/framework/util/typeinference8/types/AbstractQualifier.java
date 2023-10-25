package org.checkerframework.framework.util.typeinference8.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.framework.util.typeinference8.util.Java8InferenceContext;
import org.checkerframework.javacutil.AnnotationMirrorMap;
import org.checkerframework.javacutil.AnnotationMirrorSet;
import org.checkerframework.javacutil.AnnotationUtils;

public abstract class AbstractQualifier {
  final @Interned String hierarchyName;
  final Java8InferenceContext context;

  AbstractQualifier(AnnotationMirror anno, Java8InferenceContext context) {
    AnnotationMirror top = context.typeFactory.getQualifierHierarchy().getTopAnnotation(anno);
    hierarchyName = AnnotationUtils.annotationNameInterned(top);
    this.context = context;
  }

  public boolean sameHierarchy(AbstractQualifier t) {
    return hierarchyName == t.hierarchyName;
  }

  public static Set<AnnotationMirror> lub(
      Set<AbstractQualifier> quals, Java8InferenceContext context) {
    return combine(
        quals, context.typeFactory.getQualifierHierarchy()::leastUpperBoundQualifiersOnly);
  }

  public static Set<AnnotationMirror> glb(
      Set<AbstractQualifier> quals, Java8InferenceContext context) {
    return combine(
        quals, context.typeFactory.getQualifierHierarchy()::greatestLowerBoundQualifiersOnly);
  }

  private static Set<AnnotationMirror> combine(
      Set<AbstractQualifier> quals, BinaryOperator<AnnotationMirror> combine) {
    Map<String, AnnotationMirror> m = new HashMap<>();

    for (AbstractQualifier qual : quals) {
      AnnotationMirror lub = m.get(qual.hierarchyName);
      if (lub != null) {
        lub = combine.apply(lub, qual.resolve());
      } else {
        lub = qual.resolve();
      }
      m.put(qual.hierarchyName, lub);
    }
    return new AnnotationMirrorSet(m.values());
  }

  abstract AnnotationMirror resolve();

  public static Set<AbstractQualifier> create(
      Set<AnnotationMirror> annos,
      AnnotationMirrorMap<QualifierVar> qualifierVars,
      Java8InferenceContext context) {
    if (qualifierVars.isEmpty()) {
      return create(annos, context);
    }

    Set<AbstractQualifier> quals = new HashSet<>();
    for (AnnotationMirror anno : annos) {
      if (qualifierVars.containsKey(anno)) {
        quals.add(qualifierVars.get(anno));
      } else {
        quals.add(new Qualifier(anno, context));
      }
    }
    return quals;
  }

  public static Set<AbstractQualifier> create(
      Set<AnnotationMirror> annos, Java8InferenceContext context) {
    Set<AbstractQualifier> quals = new HashSet<>();
    for (AnnotationMirror anno : annos) {
      quals.add(new Qualifier(anno, context));
    }
    return quals;
  }
}
