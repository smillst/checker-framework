package org.checkerframework.framework.util.typeinference8.constraint;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.framework.util.typeinference8.bound.Instantiation;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.Variable;

/**
 * Created by smillst on 12/7/16.
 *
 * <p>https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.1.2
 */
public abstract class Constraint implements ReductionResult {

    public enum Kind {
        /**
         * &lt;Expression &rarr; T&gt; An expression is compatible in a loose invocation context
         * with type T
         */
        EXPRESSION,
        /** &lt;S &rarr; T&gt;: A type S is compatible in a loose invocation context with type T */
        TYPE_COMPATIBILITY,
        /** &lt;S <: T&gt;: A reference type S is a subtype of a reference type T */
        SUBTYPE,
        /** &lt;S <= T&gt;: A type argument S is contained by a type argument T. */
        CONTAINED,
        /**
         * &lt;S = T&gt;: A type S is the same as a type T, or a type argument S is the same as type
         * argument T.
         */
        TYPE_EQUALITY,
        /**
         * &lt;LambdaExpression &rarr;throws T&gt;: The checked exceptions thrown by the body of the
         * LambdaExpression are declared by the throws clause of the function type derived from T.
         */
        LAMBDA_EXCEPTION,
        /**
         * &lt;MethodReference &rarr;throws T&gt;: The checked exceptions thrown by the referenced
         * method are declared by the throws clause of the function type derived from T.
         */
        METHOD_REF_EXCEPTION,
    }
    /** T: may contain inference variables. */
    public AbstractType T;

    protected Constraint(AbstractType t) {
        assert t != null : "Can't create a constraint with a null type.";
        T = t;
    }

    public AbstractType getT() {
        return T;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Constraint that = (Constraint) o;

        return T.equals(that.T);
    }

    @Override
    public int hashCode() {
        return T.hashCode();
    }

    public abstract Kind getKind();

    public Collection<Variable> getInferenceVariables() {
        return T.getInferenceVariables();
    }

    public abstract List<Variable> getInputVariables();

    public abstract List<Variable> getOutputVariables();

    public void applyInstantiations(List<Instantiation> instantiations) {
        T = T.applyInstantiations(instantiations);
    }

    public static class Typing extends Constraint {
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
        public void applyInstantiations(List<Instantiation> instantiations) {
            super.applyInstantiations(instantiations);
            S = S.applyInstantiations(instantiations);
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

            Typing typing = (Typing) o;

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

    /**
     * &lt;LambdaExpression &rarr;throws T&gt;: The checked exceptions thrown by the body of the
     * LambdaExpression are declared by the throws clause of the function type derived from T.
     */
    public abstract static class LambdaExpression extends Constraint {
        LambdaExpressionTree lambda;

        public LambdaExpression(LambdaExpressionTree lambda, AbstractType fi) {
            super(fi);
            this.lambda = lambda;
        }

        @Override
        public Kind getKind() {
            return Kind.LAMBDA_EXCEPTION;
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

            LambdaExpression that = (LambdaExpression) o;

            return lambda != null ? lambda.equals(that.lambda) : that.lambda == null;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (lambda != null ? lambda.hashCode() : 0);
            return result;
        }
    }

    /**
     * &lt;MethodReference &rarr;throws T&gt;: The checked exceptions thrown by the referenced
     * method are declared by the throws clause of the function type derived from T.
     */
    public abstract static class MemberReferenceExpression extends Constraint {
        MemberReferenceTree memberRef;

        public MemberReferenceExpression(MemberReferenceTree memberRef, AbstractType fi) {
            super(fi);
            this.memberRef = memberRef;
        }

        @Override
        public Kind getKind() {
            return Kind.METHOD_REF_EXCEPTION;
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

            MemberReferenceExpression that = (MemberReferenceExpression) o;

            return memberRef.equals(that.memberRef);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + memberRef.hashCode();
            return result;
        }
    }
}
