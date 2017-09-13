package org.checkerframework.framework.util.typeinference8.constraint;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import java.util.Collections;
import java.util.List;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
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
        QUALIFIER_SUBTYPE;
    }
    /** T: may contain inference variables. */
    protected AbstractType T;

    protected Constraint(AbstractType t) {
        T = t;
    }

    public AbstractType getT() {
        return T;
    }

    public abstract Kind getKind();

    public abstract List<Variable> getInputVariables();

    public abstract List<Variable> getOutputVariables();

    protected void applyInstantiations(List<Instantiation> instantiations) {
        T = T.applyInstantiations(instantiations);
    }

    public static class Typing extends Constraint {
        AbstractType S;
        final Kind kind;

        public Typing(AbstractType s, AbstractType t, Kind kind) {
            super(t);
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
        protected void applyInstantiations(List<Instantiation> instantiations) {
            super.applyInstantiations(instantiations);
            S = S.applyInstantiations(instantiations);
        }
    }

    /**
     * &lt;LambdaExpression &rarr;throws T&gt;: The checked exceptions thrown by the body of the
     * LambdaExpression are declared by the throws clause of the function type derived from T.
     */
    public static class LambdaExpression extends Constraint {
        LambdaExpressionTree lambda;

        public LambdaExpression(InferenceType t) {
            super(t);
        }

        public LambdaExpression(LambdaExpressionTree lambda, AbstractType fi) {
            super(fi);
            this.lambda = lambda;
        }

        @Override
        public Kind getKind() {
            return Kind.LAMBDA_EXCEPTION;
        }

        @Override
        public List<Variable> getInputVariables() {
            //TODO:
            throw new RuntimeException("Not Implemented");
        }

        @Override
        public List<Variable> getOutputVariables() {
            //TODO:
            throw new RuntimeException("Not Implemented");
        }
    }

    /**
     * &lt;MethodReference &rarr;throws T&gt;: The checked exceptions thrown by the referenced
     * method are declared by the throws clause of the function type derived from T.
     */
    public static class MemberReferenceExpression extends Constraint {
        MemberReferenceTree memberRef;

        public MemberReferenceExpression(InferenceType t) {
            super(t);
        }

        public MemberReferenceExpression(MemberReferenceTree memberRef, AbstractType fi) {
            super(fi);
            this.memberRef = memberRef;
        }

        @Override
        public Kind getKind() {
            return Kind.METHOD_REF_EXCEPTION;
        }

        @Override
        public List<Variable> getInputVariables() {
            //TODO:
            throw new RuntimeException("Not Implemented");
        }

        @Override
        public List<Variable> getOutputVariables() {
            //TODO:
            throw new RuntimeException("Not Implemented");
        }
    }
}
