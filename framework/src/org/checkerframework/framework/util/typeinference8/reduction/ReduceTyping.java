package org.checkerframework.framework.util.typeinference8.reduction;

import java.util.ArrayDeque;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Typing;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.FalseBoundException;
import org.checkerframework.javacutil.ErrorReporter;

/** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.3-100 */
public class ReduceTyping {

    public static boolean reduceTyping(BoundSet boundSet, Typing constraint, Context context) {
        ReductionResult result = reduceTypingOneStep(constraint, context);
        ArrayDeque<Typing> constraints = new ArrayDeque<>();
        while (result != null) {
            if (result == ConstraintSet.TRUE) {
                // Do nothing
            } else if (result == ConstraintSet.FALSE) {
                boundSet.addFalse();
            } else if (result instanceof Typing) {
                constraints.push((Typing) result);
            } else if (result instanceof ConstraintSet) {
                ConstraintSet newSet = ((ConstraintSet) result);
                while (!newSet.isEmpty()) {
                    constraints.push((Typing) newSet.pop());
                }
            } else if (result == ReductionResult.UNCHECKED_CONVERSION) {
                boundSet.setUncheckedConversion(true);
            } else {
                ErrorReporter.errorAbort("Unexpected result");
                throw new RuntimeException("Error");
            }

            if (constraints.isEmpty()) {
                return true;
            }

            result = reduceTypingOneStep(constraints.pop(), context);
        }

        return false;
    }

    private static ReductionResult reduceTypingOneStep(Typing constraint, Context context) {
        ReductionResult r = constraint.reduce(context);
        if (r == null) {
            throw new FalseBoundException(constraint);
        }
        return r;
    }
}
