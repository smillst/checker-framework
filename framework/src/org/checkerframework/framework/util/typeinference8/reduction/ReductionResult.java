package org.checkerframework.framework.util.typeinference8.reduction;

public interface ReductionResult {
    ReductionResult TRUE = new ReductionResult() {};
    ReductionResult FALSE = new ReductionResult() {};
}
