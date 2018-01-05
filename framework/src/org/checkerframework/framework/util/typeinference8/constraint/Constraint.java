package org.checkerframework.framework.util.typeinference8.constraint;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.TypeKind;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.javacutil.TreeUtils;

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

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.5.2-200 */
    protected List<Variable> getInputVariablesForExpression(ExpressionTree tree, AbstractType t) {

        switch (tree.getKind()) {
            case LAMBDA_EXPRESSION:
                if (t.isVariable()) {
                    return Collections.singletonList((Variable) t);
                } else {
                    LambdaExpressionTree lambdaTree = (LambdaExpressionTree) tree;
                    List<Variable> inputs = new ArrayList<>();
                    if (TreeUtils.isImplicitlyTypeLambda(lambdaTree)) {
                        List<AbstractType> params = T.getFunctionTypeParameters();
                        if (params == null) {
                            // T is not a function type.
                            return Collections.emptyList();
                        }
                        for (AbstractType param : params) {
                            inputs.addAll(param.getInferenceVariables());
                        }
                    }
                    AbstractType R = T.getFunctionTypeReturn();
                    if (R == null || R.getTypeKind() == TypeKind.NONE) {
                        return inputs;
                    }
                    for (ExpressionTree e : TreeUtils.getReturnedExpressions(lambdaTree)) {
                        Constraint c = new Expression(e, R);
                        inputs.addAll(c.getInputVariables());
                    }
                    return inputs;
                }
            case MEMBER_REFERENCE:
                if (t.isVariable()) {
                    return Collections.singletonList((Variable) t);
                } else if (TreeUtils.isExactMethodReference((MemberReferenceTree) tree)) {
                    return Collections.emptyList();
                } else {
                    List<AbstractType> params = T.getFunctionTypeParameters();
                    if (params == null) {
                        // T is not a function type.
                        return Collections.emptyList();
                    }
                    List<Variable> inputs = new ArrayList<>();
                    for (AbstractType param : params) {
                        inputs.addAll(param.getInferenceVariables());
                    }
                    return inputs;
                }
            case PARENTHESIZED:
                return getInputVariablesForExpression(TreeUtils.skipParens(tree), t);
            case CONDITIONAL_EXPRESSION:
                ConditionalExpressionTree conditional = (ConditionalExpressionTree) tree;
                List<Variable> inputs = new ArrayList<>();
                inputs.addAll(getInputVariablesForExpression(conditional.getTrueExpression(), t));
                inputs.addAll(getInputVariablesForExpression(conditional.getFalseExpression(), t));
                return inputs;
            default:
                return Collections.emptyList();
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

    public void applyInstantiations(List<Variable> instantiations) {
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
        public void applyInstantiations(List<Variable> instantiations) {
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
     *
     * <p>&lt;MethodReference &rarr;throws T&gt;: The checked exceptions thrown by the referenced
     * method are declared by the throws clause of the function type derived from T.
     */
    public static class ThrowsConstraint extends Constraint {
        ExpressionTree expression;
        Theta map;

        public ThrowsConstraint(ExpressionTree expression, AbstractType t, Theta map) {
            super(t);
            assert expression.getKind() == Tree.Kind.LAMBDA_EXPRESSION
                    || expression.getKind() == Tree.Kind.MEMBER_REFERENCE;
            this.expression = expression;
            this.map = map;
        }

        public Theta getMap() {
            return map;
        }

        public ExpressionTree getExpression() {
            return expression;
        }

        @Override
        public Kind getKind() {
            return expression.getKind() == Tree.Kind.LAMBDA_EXPRESSION
                    ? Kind.LAMBDA_EXCEPTION
                    : Kind.METHOD_REF_EXCEPTION;
        }

        @Override
        public List<Variable> getInputVariables() {
            return getInputVariablesForExpression(expression, getT());
        }

        @Override
        public List<Variable> getOutputVariables() {
            List<Variable> input = getInputVariables();
            List<Variable> output = new ArrayList<>(getT().getInferenceVariables());
            output.removeAll(input);
            return output;
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

            ThrowsConstraint that = (ThrowsConstraint) o;

            return expression != null
                    ? expression.equals(that.expression)
                    : that.expression == null;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (expression != null ? expression.hashCode() : 0);
            return result;
        }
    }
}
