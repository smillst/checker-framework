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
    ReductionResult UNCHECKED_CONVERSION =
            new ReductionResult() {
                @Override
                public String toString() {
                    return "UNCHECKED_CONVERSION";
                }
            };
}
