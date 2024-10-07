package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.ReductionResult;
import org.checkerframework.framework.util.typeinference8.util.Java8InferenceContext;
import org.checkerframework.javacutil.AnnotationMirrorMap;
import org.checkerframework.javacutil.TypesUtils;

/** A type that does not contain any inference variables. */
public class ProperType extends AbstractType {

  /** The annotated type mirror. */
  private final AnnotatedTypeMirror type;

  /** A mapping from polymorphic annotation to {@link QualifierVar}. */
  private final AnnotationMirrorMap<QualifierVar> qualifierVars;

  /**
   * Creates a proper type.
   *
   * @param type the annotated type
   * @param context the context
   */
  public ProperType(AnnotatedTypeMirror type, Java8InferenceContext context) {
    this(type, AnnotationMirrorMap.emptyMap(), context);
  }

  /**
   * Creates a proper type.
   *
   * @param type the annotated type
   * @param qualifierVars a mapping from polymorphic annotation to {@link QualifierVar}
   * @param context the context
   */
  public ProperType(
      AnnotatedTypeMirror type,
      AnnotationMirrorMap<QualifierVar> qualifierVars,
      Java8InferenceContext context) {
    super(context);
    this.type = type;
    this.qualifierVars = qualifierVars;
  }

  /**
   * Creates a proper type from the type of the expression.
   *
   * @param tree an expression tree
   * @param context the context
   */
  public ProperType(ExpressionTree tree, Java8InferenceContext context) {
    super(context);
    this.type = context.typeFactory.getAnnotatedType(tree);
    this.qualifierVars = AnnotationMirrorMap.emptyMap();
  }

  /**
   * Creates a proper type from the type of the variable.
   *
   * @param varTree a variable tree
   * @param context the context
   */
  public ProperType(VariableTree varTree, Java8InferenceContext context) {
    super(context);
    this.type = context.typeFactory.getAnnotatedType(varTree);
    this.qualifierVars = AnnotationMirrorMap.emptyMap();
  }

  @Override
  public Kind getKind() {
    return Kind.PROPER;
  }

  @Override
  public AbstractType create(AnnotatedTypeMirror atm) {
    return new ProperType(atm, qualifierVars, context);
  }

  /**
   * If this is a primitive type, then the proper type corresponding to its wrapper is returned.
   * Otherwise, this object is return.
   *
   * @return the proper type that is the wrapper type for this type or this if no such wrapper
   *     exists
   */
  public ProperType boxType() {
    if (type.getKind().isPrimitive()) {
      return new ProperType(
          typeFactory.getBoxedType((AnnotatedPrimitiveType) getAnnotatedType()), context);
    }
    return this;
  }

  /**
   * Is {@code this} a subtype of {@code superType}?
   *
   * @param superType super type
   * @return if {@code this} is a subtype of {@code superType}, then return {@link
   *     ConstraintSet#TRUE}; otherwise, a false bound is returned
   */
  public ReductionResult isSubType(ProperType superType) {
    TypeMirror subType = getJavaType();
    TypeMirror superJavaType = superType.getJavaType();

    if (context.typeFactory.types.isAssignable(subType, superJavaType)
        || TypesUtils.isErasedSubtype(subType, superJavaType, context.typeFactory.types)) {
      AnnotatedTypeMirror superATM = superType.getAnnotatedType();
      AnnotatedTypeMirror subATM = this.getAnnotatedType();
      if (typeFactory.getTypeHierarchy().isSubtype(subATM, superATM)) {
        return ConstraintSet.TRUE;
      } else {
        return ConstraintSet.TRUE_ANNO_FAIL;
      }
    } else {
      return ConstraintSet.FALSE;
    }
  }

  /**
   * Is {@code this} an unchecked subtype of {@code superType}?
   *
   * @param superType super type
   * @return if {@code this} is an unchecked subtype of {@code superType}, then return {@link
   *     ConstraintSet#TRUE}; otherwise, a false bound is returned
   */
  public ReductionResult isSubTypeUnchecked(ProperType superType) {
    TypeMirror subType = getJavaType();
    TypeMirror superJavaType = superType.getJavaType();

    if (context.types.isSubtypeUnchecked((Type) subType, (Type) superJavaType)) {
      AnnotatedTypeMirror superATM = superType.getAnnotatedType();
      AnnotatedTypeMirror subATM = this.getAnnotatedType();
      if (typeFactory.getTypeHierarchy().isSubtype(subATM, superATM)) {
        return ConstraintSet.TRUE;
      } else {
        return ConstraintSet.TRUE_ANNO_FAIL;
      }
    } else {
      return ConstraintSet.FALSE;
    }
  }

  /**
   * Is {@code this} assignable to {@code superType}?
   *
   * @param superType super type
   * @return if {@code this} assignable to {@code superType}, then return {@link
   *     ConstraintSet#TRUE}; otherwise, a false bound is returned
   */
  public ReductionResult isAssignable(ProperType superType) {
    TypeMirror subType = getJavaType();
    TypeMirror superJavaType = superType.getJavaType();

    if (context.types.isAssignable((Type) subType, (Type) superJavaType)) {
      AnnotatedTypeMirror superATM = superType.getAnnotatedType();
      AnnotatedTypeMirror subATM = this.getAnnotatedType();
      if (typeFactory.getTypeHierarchy().isSubtype(subATM, superATM)) {
        return ConstraintSet.TRUE;
      } else {
        return ConstraintSet.TRUE_ANNO_FAIL;
      }
    } else {
      return ConstraintSet.FALSE;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    ProperType that = (ProperType) o;

    if (!type.equals(that.type)) {
      return false;
    }
    return Objects.equals(qualifierVars, that.qualifierVars);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + type.hashCode();
    result = 31 * result + (qualifierVars != null ? qualifierVars.hashCode() : 0);
    return result;
  }

  @Override
  public TypeMirror getJavaType() {
    return type.getUnderlyingType();
  }

  @Override
  public AnnotatedTypeMirror getAnnotatedType() {
    return type;
  }

  @Override
  public boolean isObject() {
    return TypesUtils.isObject(type.getUnderlyingType());
  }

  @Override
  public Collection<Variable> getInferenceVariables() {
    return Collections.emptyList();
  }

  @Override
  public AbstractType applyInstantiations() {
    return this;
  }

  @Override
  public Set<AbstractQualifier> getQualifiers() {
    return AbstractQualifier.create(
        getAnnotatedType().getPrimaryAnnotations(), qualifierVars, context);
  }

  @Override
  public String toString() {
    return type.toString();
  }
}
