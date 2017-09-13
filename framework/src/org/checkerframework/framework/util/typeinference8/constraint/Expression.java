package org.checkerframework.framework.util.typeinference8.constraint;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.InternalUtils;
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

    private final ExpressionKind expressionKind;

    public Expression(ExpressionTree expressionTree, AbstractType t) {
        super(t);
        this.expression = expressionTree;

        if (t.isProper()) {
            this.expressionKind = ExpressionKind.PROPER_TYPE;
        } else if (InternalUtils.isStandaloneExpression(expressionTree)) {
            this.expressionKind = ExpressionKind.STANDALONE;
        } else if (expressionTree.getKind() == Tree.Kind.PARENTHESIZED) {
            this.expressionKind = ExpressionKind.PARENTHESIZED;
        } else if (expressionTree.getKind() == Tree.Kind.NEW_CLASS
                || expressionTree.getKind() == Tree.Kind.METHOD_INVOCATION) {
            this.expressionKind = ExpressionKind.METHOD_INVOCATION;
        } else if (expressionTree.getKind() == Tree.Kind.CONDITIONAL_EXPRESSION) {
            this.expressionKind = ExpressionKind.CONDITIONAL;
        } else if (expressionTree.getKind() == Tree.Kind.LAMBDA_EXPRESSION) {
            this.expressionKind = ExpressionKind.LAMBDA;
        } else if (expressionTree.getKind() == Tree.Kind.MEMBER_REFERENCE) {
            this.expressionKind = ExpressionKind.METHOD_REF;
        } else {
            ErrorReporter.errorAbort(
                    "Unexpected expression kind: %s, Expression: %s",
                    expressionTree.getKind(), expressionTree);
            throw new RuntimeException();
        }
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
        return expressionKind;
    }

    public ExpressionTree getExpression() {
        return expression;
    }

    private List<Variable> getInputVariablesForExpression(ExpressionTree tree, AbstractType t) {

        switch (tree.getKind()) {
            case LAMBDA_EXPRESSION:
                if (t.isVariable()) {
                    return Collections.singletonList((Variable) t);
                } else {
                    // TODO
                    throw new RuntimeException("Not Implemented");
                }
            case MEMBER_REFERENCE:
                if (t.isVariable()) {
                    return Collections.singletonList((Variable) t);
                } else {
                    // TODO
                    throw new RuntimeException("Not Implemented");
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
}
