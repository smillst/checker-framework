package org.checkerframework.framework.util.typeinference8.reduction;

import org.checkerframework.framework.util.typeinference8.bound.Bound;
import org.checkerframework.framework.util.typeinference8.bound.Bound.Kind;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.LambdaExpression;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.MemberReferenceExpression;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Expression;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.javacutil.ErrorReporter;

public class Reduce {
    public BoundSet reduce(ConstraintSet constraintSet, Context context) {
        BoundSet boundSet = new BoundSet(context);
        while (!constraintSet.isEmpty()) {
            Constraint constraint = constraintSet.pop();
            ReductionResult result = reduce(constraint, context);
            if (result instanceof Constraint) {
                constraintSet.add((Constraint) result);
            } else if (result instanceof ConstraintSet) {
                constraintSet.add((ConstraintSet) result);
            } else if (result instanceof Bound) {
                if (((Bound) result).getKind() == Kind.FALSE) {
                    return BoundSet.FALSE;
                }
                boundSet.add((Bound) result);
            } else if (result instanceof BoundSet) {
                if (((BoundSet) result).containsFalse()) {
                    return BoundSet.FALSE;
                }
                boundSet.add((BoundSet) result);
            } else {
                throw new RuntimeException("Not found " + result);
            }
        }
        return boundSet;
    }

    public ReductionResult reduce(Constraint constraint, Context context) {
        switch (constraint.getKind()) {
            case EXPRESSION:
                return ReduceExpression.reduce((Expression) constraint, context);
            case TYPE_COMPATIBILITY:
                return ReduceTyping.reduceCompatible((Typing) constraint);
            case SUBTYPE:
                return ReduceTyping.reduceSubtyping((Typing) constraint, context);
            case CONTAINED:
                return ReduceTyping.reduceContained((Typing) constraint);
            case TYPE_EQUALITY:
                return ReduceTyping.reduceEquality((Typing) constraint);
            case LAMBDA_EXCEPTION:
                return reduceLambdaExpression((LambdaExpression) constraint);
            case METHOD_REF_EXCEPTION:
                return reduceMemberReferenceExpression((MemberReferenceExpression) constraint);
            case QUALIFIER_SUBTYPE:
                throw new RuntimeException("Not implemented");
            default:
                ErrorReporter.errorAbort("Unexpected constraint kind: " + constraint.getKind());
                throw new RuntimeException(""); // dead code
        }
    }

    public ReductionResult reduceLambdaExpression(LambdaExpression c) {
        // TODO: https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.5-100
        throw new RuntimeException("Not Implemented");
    }

    public ReductionResult reduceMemberReferenceExpression(MemberReferenceExpression c) {
        // TODO: https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.5-200
        throw new RuntimeException("Not Implemented");
    }
}
