package org.checkerframework.framework.util.typeinference8.constraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.framework.util.typeinference8.reduction.ReduceTyping;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.javacutil.ErrorReporter;

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
                return ReduceTyping.reduceSubtyping(this, context);
            case CONTAINED:
                return ReduceTyping.reduceContained(this);
            case TYPE_EQUALITY:
                return ReduceTyping.reduceEquality(this);
            default:
                ErrorReporter.errorAbort("Unexpected kind: " + getKind());
                throw new RuntimeException();
        }
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
