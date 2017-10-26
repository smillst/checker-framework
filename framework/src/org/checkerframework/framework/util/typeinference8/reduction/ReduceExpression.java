package org.checkerframework.framework.util.typeinference8.reduction;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Expression;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.TreeUtils;

public class ReduceExpression {
    /** See JLS 18.2.1 */
    public static ReductionResult reduce(Expression constraint, Context context) {
        switch (constraint.getExpressionKind()) {
            case PROPER_TYPE:
                return reduceProperType(constraint);
            case STANDALONE:
                return reduceStandalone(constraint, context);
            case PARENTHESIZED:
                return reduceParenthesized(constraint);
            case METHOD_INVOCATION:
                return reduceMethodInvocation(constraint, context);
            case CONDITIONAL:
                return reduceConditional(constraint);
            case LAMBDA:
                return reduceLambda(
                        constraint.getT(),
                        (LambdaExpressionTree) constraint.getExpression(),
                        context);
            case METHOD_REF:
                return reduceMethodRef(
                        constraint.getT(),
                        (MemberReferenceTree) constraint.getExpression(),
                        context);
            default:
                ErrorReporter.errorAbort("Unexpected ExpressionKind: %s", constraint.getKind());
                return BoundSet.FALSE;
        }
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.1-300 */
    private static ReductionResult reduceMethodRef(
            AbstractType t, MemberReferenceTree memRef, Context context) {
        if (InternalInferenceUtils.isExact(memRef)) {
            ConstraintSet constraintSet = new ConstraintSet();
            List<AbstractType> ps = t.getFunctionTypeParameters();
            List<AbstractType> fs = new ArrayList<>();
            for (TypeMirror param : InternalInferenceUtils.getParametersOfPAMethod(memRef)) {
                fs.add(new ProperType(param, context));
            }

            if (ps.size() == fs.size() + 1) {
                AbstractType targetReference = ps.remove(0);
                ProperType referenceType =
                        new ProperType(
                                InternalUtils.typeOf(memRef.getQualifierExpression()), context);
                constraintSet.add(
                        new Typing(targetReference, referenceType, Constraint.Kind.SUBTYPE));
            }
            for (int i = 0; i < ps.size(); i++) {
                constraintSet.add(new Typing(ps.get(i), fs.get(i), Constraint.Kind.SUBTYPE));
            }
            return constraintSet;
        }
        // else

        if (memRef.getTypeArguments() == null && InternalInferenceUtils.isGenericMethod(memRef)) {
            // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.1-300-D-B-BC
            // Otherwise, if the method reference expression elides TypeArguments, and the
            // compile-time declaration is a generic method, and the return type of the
            // compile-time declaration mentions at least one of the method's type parameters,
            // then
            // the constraint reduces to the bound set B3 which would be used to determine the
            // method reference's invocation type when targeting the return type of the function
            // type, as defined in 18.5.2. B3 may contain new inference variables, as well as
            // dependencies between these new variables and the inference variables in T.

            // compile-time declaration and invocation: ((JCMemberReference)memRef).sym
            // function type =     type(InternalUtils.findFunction());

        } else {
            // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.1-300-D-B-C
            // Otherwise, let R be the return type of the function type, and let R' be the result
            // of applying capture conversion (§5.1.10) to the return type of the invocation type
            // (§15.12.2.6) of the compile-time declaration. If R' is void, the constraint reduces
            // to false; otherwise, the constraint reduces to ‹R' → R›.
        }

        // TODO: implement
        throw new RuntimeException("Not implemented");
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.1-200 */
    private static ReductionResult reduceLambda(
            AbstractType t, LambdaExpressionTree lambda, Context context) {
        AbstractType tPrime = getGroundTargetType(t, lambda);
        ConstraintSet constraintSet = new ConstraintSet();

        if (!InternalInferenceUtils.isImplicitlyType(lambda)) {
            // Explicitly typed lambda
            List<? extends VariableTree> parameters = lambda.getParameters();
            List<AbstractType> gs = t.getFunctionTypeParameters();
            assert parameters.size() == gs.size();

            for (int i = 0; i < gs.size(); i++) {
                VariableTree parameter = parameters.get(i);
                AbstractType fi = new ProperType(InternalUtils.typeOf(parameter), context);
                AbstractType gi = gs.get(i);
                constraintSet.add(new Typing(fi, gi, Constraint.Kind.TYPE_EQUALITY));
            }
            constraintSet.add(new Typing(tPrime, t, Constraint.Kind.SUBTYPE));
        }

        AbstractType R = t.getFunctionTypeReturn();
        if (R != null && R.getTypeKind() != TypeKind.VOID) {
            for (ExpressionTree e : InternalInferenceUtils.getReturnedExpressions(lambda)) {
                if (R.isProper()) {
                    if (!context.factory
                            .getContext()
                            .getTypeUtils()
                            .isAssignable(
                                    InternalUtils.typeOf(e), ((ProperType) R).getProperType())) {
                        return BoundSet.FALSE;
                    }
                } else {
                    constraintSet.add(new Expression(e, R));
                }
            }
        }
        return constraintSet;
    }

    private static ConstraintSet reduceConditional(Expression constraint) {
        ConditionalExpressionTree conditional =
                (ConditionalExpressionTree) constraint.getExpression();
        Constraint trueConstraint =
                new Expression(conditional.getTrueExpression(), constraint.getT());
        Constraint falseConstraint =
                new Expression(conditional.getFalseExpression(), constraint.getT());
        return new ConstraintSet(trueConstraint, falseConstraint);
    }

    /**
     * Text from JLS 18.2.1: If the expression is a class instance creation expression or a method
     * invocation expression, the constraint reduces to the bound set B3 which would be used to
     * determine the expression's invocation type when targeting T, as defined in 18.5.2. (For a
     * class instance creation expression, the corresponding "method" used for inference is defined
     * in 15.9.3).
     *
     * <p>This bound set may contain new inference variables, as well as dependencies between these
     * new variables and the inference variables in T.
     */
    private static ReductionResult reduceMethodInvocation(Expression constraint, Context context) {
        ExpressionTree expressionTree = constraint.getExpression();
        List<? extends ExpressionTree> args;
        if (expressionTree.getKind() == Kind.NEW_CLASS) {
            NewClassTree newClassTree = (NewClassTree) expressionTree;
            args = newClassTree.getArguments();
        } else {
            MethodInvocationTree methodInvocationTree = (MethodInvocationTree) expressionTree;
            args = methodInvocationTree.getArguments();
        }

        ExecutableType methodType =
                InternalInferenceUtils.getTypeOfMethodAdaptedToUse(expressionTree, context);
        Theta map = Theta.theta(methodType, expressionTree, context);
        BoundSet b2 = context.inference.createB2(expressionTree, methodType, args, map);
        return context.inference.createB3(b2, methodType, expressionTree, constraint.getT(), map);
    }

    private static Constraint reduceParenthesized(Expression constraint) {
        assert constraint.getExpression().getKind() == Kind.PARENTHESIZED;
        return new Expression(TreeUtils.skipParens(constraint.getExpression()), constraint.getT());
    }

    private static Constraint reduceStandalone(Expression constraint, Context context) {
        ProperType s = new ProperType(InternalUtils.typeOf(constraint.getExpression()), context);
        return new Typing(s, constraint.getT(), Constraint.Kind.TYPE_COMPATIBILITY);
    }

    /**
     * JSL 18.2.1: "If T is a proper type, the constraint reduces to true if the expression is
     * compatible in a loose invocation context with T (5.3), and false otherwise."
     */
    private static BoundSet reduceProperType(Expression constraint) {
        // Assume the constraint reduces to TRUE, if it did not the code wouldn't compile with
        // javac.

        // com.sun.tools.javac.code.Types.isConvertible(com.sun.tools.javac.code.Type, com.sun.tools.javac.code.Type)
        return BoundSet.TRUE;
    }

    private static AbstractType getGroundTargetType(AbstractType t, LambdaExpressionTree lambda) {
        if (!t.isWildcardParameterizedType()) {
            return t;
        }
        // If T is a wildcard-parameterized functional interface type and the lambda expression is
        // explicitly typed, then the ground target type is inferred as described in 18.5.3.
        if (InternalInferenceUtils.isImplicitlyType(lambda)) {
            // TODO: call 18.5.3: Functional Interface Parameterization Inference
            throw new RuntimeException("Not implemented");
        } else {
            // If T is a wildcard-parameterized functional interface type and the lambda expression
            // is implicitly typed, then the ground target type is the non-wildcard parameterization (§9.9) of T.
            // https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.9-200-C
            return t.getNonWildcardParameterization();
        }
    }
}
