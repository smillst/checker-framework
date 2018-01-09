package org.checkerframework.framework.util.typeinference8.constraint;

import org.checkerframework.framework.util.typeinference8.bound.BoundSet;

public interface ReductionResult {
    ReductionResult TRUE =
            new ReductionResult() {
                @Override
                public String toString() {
                    return "TRUE";
                }
            };
    ReductionResult FALSE =
            new ReductionResult() {
                @Override
                public String toString() {
                    return "FALSE";
                }
            };
    ReductionResult UNCHECKED_CONVERSION =
            new ReductionResult() {
                @Override
                public String toString() {
                    return "UNCHECKED_CONVERSION";
                }
            };

     class ReductionResultPair implements ReductionResult {
        public ConstraintSet first;
        public BoundSet second;

        public static ReductionResultPair of(ConstraintSet first, BoundSet second) {
            ReductionResultPair pair = new ReductionResultPair();
            pair.first = first;
            pair.second = second;
            return pair;
        }
    }
}
