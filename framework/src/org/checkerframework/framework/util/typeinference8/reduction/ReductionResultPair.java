package org.checkerframework.framework.util.typeinference8.reduction;

import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;

public class ReductionResultPair implements ReductionResult {
    ConstraintSet first;
    BoundSet second;

    static ReductionResultPair of(ConstraintSet first, BoundSet second) {
        ReductionResultPair pair = new ReductionResultPair();
        pair.first = first;
        pair.second = second;
        return pair;
    }
}
