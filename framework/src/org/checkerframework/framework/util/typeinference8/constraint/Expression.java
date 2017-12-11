package org.checkerframework.framework.util.typeinference8.constraint;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.type.TypeKind;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.TreeUtils;

/**
 * &lt;Expression &rarr; T&gt; An expression is compatible in a loose invocation context with type T
 */
public class Expression extends Constraint {

    public enum ExpressionKind {
        PROPER_TYPE,
        STANDALONE, // (Not a poly expression https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.2)
        PARENTHESIZED,
        METHOD_INVOCATION, // includes class creation expressions
        CONDITIONAL,
        LAMBDA,
        METHOD_REF;
    }

    private final ExpressionTree expression;

    public Expression(ExpressionTree expressionTree, AbstractType t) {
        super(t);
        this.expression = expressionTree;
    }

    @Override
    public Kind getKind() {
        return Kind.EXPRESSION;
    }

    @Override
    public List<Variable> getInputVariables() {
        return getInputVariablesForExpression(expression, getT());
    }

    public List<Variable> getOutputVariables() {
        List<Variable> input = getInputVariables();
        List<Variable> output = new ArrayList<>(getT().getInferenceVariables());
        output.removeAll(input);
        return output;
    }

    public ExpressionKind getExpressionKind() {
        // Compute each time because T might become a proper type when instantiations are applied.
        if (getT().isProper()) {
            return ExpressionKind.PROPER_TYPE;
        } else if (InternalInferenceUtils.isStandaloneExpression(expression)) {
            return ExpressionKind.STANDALONE;
        }
        switch (expression.getKind()) {
            case PARENTHESIZED:
                return ExpressionKind.PARENTHESIZED;
            case NEW_CLASS:
            case METHOD_INVOCATION:
                return ExpressionKind.METHOD_INVOCATION;
            case CONDITIONAL_EXPRESSION:
                return ExpressionKind.CONDITIONAL;
            case LAMBDA_EXPRESSION:
                return ExpressionKind.LAMBDA;
            case MEMBER_REFERENCE:
                return ExpressionKind.METHOD_REF;
            default:
                ErrorReporter.errorAbort(
                        "Unexpected expression kind: %s, Expression: %s",
                        expression.getKind(), expression);
                throw new RuntimeException();
        }
    }

    public ExpressionTree getExpression() {
        return expression;
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.5.2-200 */
    private List<Variable> getInputVariablesForExpression(ExpressionTree tree, AbstractType t) {

        switch (tree.getKind()) {
            case LAMBDA_EXPRESSION:
                if (t.isVariable()) {
                    return Collections.singletonList((Variable) t);
                } else {
                    LambdaExpressionTree lambdaTree = (LambdaExpressionTree) tree;
                    List<Variable> inputs = new ArrayList<>();
                    if (InternalInferenceUtils.isImplicitlyType(lambdaTree)) {
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
                    for (ExpressionTree e :
                            InternalInferenceUtils.getReturnedExpressions(lambdaTree)) {
                        Constraint c = new Expression(e, R);
                        inputs.addAll(c.getInputVariables());
                    }
                    return inputs;
                }
            case MEMBER_REFERENCE:
                if (t.isVariable()) {
                    return Collections.singletonList((Variable) t);
                } else if (InternalInferenceUtils.isExact((MemberReferenceTree) tree)) {
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
    public String toString() {
        return expression + " -> " + T;
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

        Expression that = (Expression) o;

        return expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + expression.hashCode();
        return result;
    }
}
