package org.checkerframework.framework.util.typeinference8.reduction;

import org.checkerframework.framework.util.typeinference8.bound.Bound;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.LambdaExpression;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.MemberReferenceExpression;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Expression;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.util.Context;

public class Reduce {
    public BoundSet reduce(ConstraintSet constraintSet, Theta map, Context context) {
        BoundSet boundSet = new BoundSet(context);
        while (!constraintSet.isEmpty()) {
            Constraint constraint = constraintSet.pop();
            ReductionResult result = reduce(constraint, map, context);
            if (result instanceof Constraint) {
                constraintSet.add((Constraint) result);
            } else if (result instanceof ConstraintSet) {
                constraintSet.add((ConstraintSet) result);
            } else if (result instanceof Bound) {
                boundSet.add((Bound) result);
            } else if (result instanceof BoundSet) {
                boundSet.add((BoundSet) result);
            } else {
                throw new RuntimeException("Not found " + result);
            }
        }
        return boundSet;
    }

    public ReductionResult reduce(Constraint constraint, Theta map, Context context) {
        switch (constraint.getKind()) {
            case EXPRESSION:
                return ReduceExpression.reduce((Expression) constraint, map, context);
            case TYPE_COMPATIBILITY:
                return ReduceTyping.reduceCompatible((Typing) constraint, context);
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
                throw new RuntimeException("Not Implemented");
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
