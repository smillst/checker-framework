package org.checkerframework.framework.util.typeinference8.reduction;

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
}
