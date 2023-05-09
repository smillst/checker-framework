package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import java.util.Iterator;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.util.typeinference8.InferenceResult;
import org.checkerframework.framework.util.typeinference8.types.AbstractType.Kind;
import org.checkerframework.framework.util.typeinference8.types.VariableBounds.BoundKind;
import org.checkerframework.framework.util.typeinference8.util.Java8InferenceContext;
import org.checkerframework.framework.util.typeinference8.util.Theta;

/** An inference variable. */
@Interned public class Variable {

  /** Bounds of this variable. */
  protected final VariableBounds variableBounds;

  /** Identification number. Used only to make debugging easier. */
  protected final int id;

  /**
   * The expression for which this variable is being solved. Used to differentiate inference
   * variables for two different invocations of the same method.
   */
  protected final ExpressionTree invocation;

  /** Type variable for which the instantiation of this variable is a type argument, */
  protected final TypeVariable typeVariableJava;

  /** Type variable for which the instantiation of this variable is a type argument, */
  protected final AnnotatedTypeVariable typeVariable;

  protected final Theta map;

  protected final Java8InferenceContext context;

  Variable(
      AnnotatedTypeVariable typeVariable,
      TypeVariable typeVariableJava,
      ExpressionTree invocation,
      Java8InferenceContext context,
      Theta map) {
    this(typeVariable, typeVariableJava, invocation, context, map, context.getNextVariableId());
  }

  @SuppressWarnings("interning:argument") // "this" is interned
  Variable(
      AnnotatedTypeVariable typeVariable,
      TypeVariable typeVariableJava,
      ExpressionTree invocation,
      Java8InferenceContext context,
      Theta map,
      int id) {
    this.context = context;
    assert typeVariable != null;
    this.variableBounds = new VariableBounds(this, context);
    this.typeVariableJava = typeVariableJava;
    this.typeVariable = typeVariable;
    this.invocation = invocation;
    this.map = map;
    this.id = id;
  }

  /**
   * Return this variable's current bounds.
   *
   * @return this variable's current bounds
   */
  public VariableBounds getBounds() {
    return variableBounds;
  }

  /**
   * Adds the initial bounds to this variable. These are the bounds implied by the upper bounds of
   * the type variable. See end of JLS 18.1.3.
   *
   * @param map used to determine if the bounds refer to another variable
   */
  public void initialBounds(Theta map) {
    TypeMirror upperBound = typeVariableJava.getUpperBound();
    // If Pl has no TypeBound, the bound {@literal al <: Object} appears in the set. Otherwise,
    // for each type T delimited by & in the TypeBound, the bound {@literal al <: T[P1:=a1,...,
    // Pp:=ap]} appears in the set; if this results in no proper upper bounds for al (only
    // dependencies), then the bound {@literal al <: Object} also appears in the set.
    switch (upperBound.getKind()) {
      case INTERSECTION:
        Iterator<? extends TypeMirror> iter =
            ((IntersectionType) upperBound).getBounds().iterator();
        for (AnnotatedTypeMirror bound : typeVariable.getUpperBound().directSupertypes()) {
          AbstractType t1 = InferenceType.create(bound, iter.next(), map, context);
          variableBounds.addBound(BoundKind.UPPER, t1);
        }
        break;
      default:
        AbstractType t1 =
            InferenceType.create(typeVariable.getUpperBound(), upperBound, map, context);
        variableBounds.addBound(BoundKind.UPPER, t1);
        break;
    }

    variableBounds.addQualifierBound(
        BoundKind.LOWER, typeVariable.getLowerBound().getAnnotations());
  }

  public ExpressionTree getInvocation() {
    return invocation;
  }

  @SuppressWarnings("interning:not.interned") // Checking for exact object.
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Variable variable = (Variable) o;
    return InferenceResult.sames(typeVariableJava, variable.typeVariableJava)
        && invocation == variable.invocation;
  }

  @Override
  public int hashCode() {
    int result = typeVariableJava.toString().hashCode();
    result = 31 * result + Kind.USE_OF_VARIABLE.hashCode();
    result = 31 * result + invocation.hashCode();
    return result;
  }

  @Override
  public String toString() {
    if (variableBounds.hasInstantiation()) {
      return "a" + id + " := " + variableBounds.getInstantiation();
    }
    return "a" + id;
  }

  public ProperType getInstantiation() {
    return variableBounds.getInstantiation();
  }

  /** in case the first attempt at resolution fails. */
  public void save() {
    variableBounds.save();
  }

  /**
   * Restore the bounds to the state previously saved. This method is called if the first attempt at
   * resolution fails.
   */
  public void restore() {
    variableBounds.restore();
  }

  /**
   * Returns whether or not this variable was created for a capture bound.
   *
   * @return whether or not this variable was created for a capture bound
   */
  public boolean isCaptureVariable() {
    return false;
  }

  public TypeVariable getJavaType() {
    return typeVariableJava;
  }
}
