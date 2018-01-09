package org.checkerframework.framework.util.typeinference8.constraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.typeinference8.reduction.ReduceTyping;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.types.Variable.InferBound;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.TypesUtils;

public class Typing extends Constraint {
    AbstractType S;
    final Kind kind;

    public Typing(AbstractType s, AbstractType t, Kind kind) {
        super(t);
        assert s != null;
        this.S = s;
        this.kind = kind;
    }

    public AbstractType getS() {
        return S;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public List<Variable> getInputVariables() {
        return Collections.emptyList();
    }

    @Override
    public List<Variable> getOutputVariables() {
        return Collections.emptyList();
    }

    @Override
    public List<Variable> getInferenceVariables() {
        Set<Variable> vars = new HashSet<>();
        vars.addAll(T.getInferenceVariables());
        vars.addAll(S.getInferenceVariables());
        return new ArrayList<>(vars);
    }

    @Override
    public void applyInstantiations(List<Variable> instantiations) {
        super.applyInstantiations(instantiations);
        S = S.applyInstantiations(instantiations);
    }

    @Override
    public ReductionResult reduce(Context context) {
        switch (getKind()) {
            case TYPE_COMPATIBILITY:
                return ReduceTyping.reduceCompatible(this, context);
            case SUBTYPE:
                return reduceSubtyping(context);
            case CONTAINED:
                return ReduceTyping.reduceContained(this);
            case TYPE_EQUALITY:
                return ReduceTyping.reduceEquality(this);
            default:
                ErrorReporter.errorAbort("Unexpected kind: " + getKind());
                throw new RuntimeException();
        }
    }

    private ReductionResult reduceSubtyping(Context context) {
        if (S.isProper() && T.isProper()) {
            TypeMirror subType = S.getJavaType();
            TypeMirror superType = T.getJavaType();
            if (subType == superType) {
                return ConstraintSet.TRUE;
            }

            if (context.env.getTypeUtils().isAssignable(subType, superType)) {
                return ConstraintSet.TRUE;
            } else {
                return ConstraintSet.FALSE;
            }
        } else if (S.getTypeKind() == TypeKind.NULL) {
            return ConstraintSet.TRUE;
        } else if (T.getTypeKind() == TypeKind.NULL) {
            return null;
        }

        if (S.isVariable() || T.isVariable()) {
            if (S.isVariable()) {
                if (T.getTypeKind() == TypeKind.TYPEVAR && T.hasLowerBound()) {
                    ((Variable) S).addBound(InferBound.UPPER, T.getTypeVarLowerBound());
                } else {
                    ((Variable) S).addBound(InferBound.UPPER, T);
                }
            }
            if (T.isVariable()) {
                if (TypesUtils.isCaptured(S.getJavaType())) {
                    ((Variable) T).addBound(InferBound.LOWER, S.getTypeVarUpperBound());
                }
                ((Variable) T).addBound(InferBound.LOWER, S);
            }
            return ConstraintSet.TRUE;
        }

        switch (T.getTypeKind()) {
            case DECLARED:
                return reduceSubtypeClass();
            case ARRAY:
                return reduceSubtypeArray();
            case WILDCARD:
            case TYPEVAR:
                return reduceSubtypeTypeVariable();
            case INTERSECTION:
                return reduceSubtypingIntersection();
            default:
                return null;
        }
    }

    private ConstraintSet reduceSubtypeClass() {
        if (T.isParameterizedType()) {
            // let A1, ..., An be the type arguments of T. Among the supertypes of S, a
            // corresponding class or interface type is identified, with type arguments B1, ...,
            // Bn. If no such type exists, the constraint reduces to false. Otherwise, the
            // constraint reduces to the following new constraints:
            // for all i (1 <= i <= n), <Bi <= Ai>.

            TypeMirror tTypeMirror = T.getJavaType();
            AbstractType sAsSuper = S.asSuper(tTypeMirror);
            if (sAsSuper == null) {
                return null;
            }

            List<AbstractType> Bs = sAsSuper.getTypeArguments();
            Iterator<AbstractType> As = T.getTypeArguments().iterator();
            ConstraintSet set = new ConstraintSet();
            for (AbstractType b : Bs) {
                AbstractType a = As.next();
                set.add(new Typing(b, a, Kind.CONTAINED));
            }

            return set;
        } else {
            //the constraint reduces to true if T is among the supertypes of S, and false otherwise.
            return ConstraintSet.TRUE;
        }
    }

    private ReductionResult reduceSubtypeArray() {
        AbstractType msArrayType = S.getMostSpecificArrayType();
        if (msArrayType == null) {
            return null;
        }
        if (msArrayType.isPrimitiveArray() && T.isPrimitiveArray()) {
            return ConstraintSet.TRUE;
        } else {
            return new Typing(msArrayType.getComponentType(), T.getComponentType(), Kind.SUBTYPE);
        }
    }

    private ReductionResult reduceSubtypeTypeVariable() {
        if (S.getTypeKind() == TypeKind.INTERSECTION) {
            return ConstraintSet.TRUE;
        } else if (T.getTypeKind() == TypeKind.TYPEVAR && T.hasLowerBound()) {
            return new Typing(S, T.getTypeVarLowerBound(), Kind.SUBTYPE);
        } else if (T.getTypeKind() == TypeKind.WILDCARD && T.isLowerBoundedWildcard()) {
            return new Typing(S, T.getWildcardLowerBound(), Kind.SUBTYPE);
        } else {
            return null;
        }
    }

    private ReductionResult reduceSubtypingIntersection() {
        ConstraintSet constraintSet = new ConstraintSet();
        for (AbstractType bound : T.getIntersectionBounds()) {
            constraintSet.add(new Typing(S, bound, Kind.SUBTYPE));
        }
        return constraintSet;
    }

    @Override
    public String toString() {
        switch (kind) {
            case TYPE_COMPATIBILITY:
                return S + " -> " + T;
            case SUBTYPE:
                return S + " <: " + T;
            case CONTAINED:
                return S + " <= " + T;
            case TYPE_EQUALITY:
                return S + " = " + T;
            default:
                assert false;
                return super.toString();
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

        org.checkerframework.framework.util.typeinference8.constraint.Typing typing =
                (org.checkerframework.framework.util.typeinference8.constraint.Typing) o;

        if (!S.equals(typing.S)) {
            return false;
        }
        return kind == typing.kind;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + S.hashCode();
        result = 31 * result + kind.hashCode();
        return result;
    }
}
