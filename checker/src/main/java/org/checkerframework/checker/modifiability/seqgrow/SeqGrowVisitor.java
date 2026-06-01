package org.checkerframework.checker.modifiability.seqgrow;

import org.checkerframework.checker.modifiability.ModifiabilityVisitor;
import org.checkerframework.common.basetype.BaseTypeChecker;

/** Visitor for the {@link SeqGrowChecker}. */
public class SeqGrowVisitor extends ModifiabilityVisitor {

  /**
   * Creates a visitor for the SeqGrow Checker.
   *
   * @param checker the SeqGrow Checker
   */
  public SeqGrowVisitor(BaseTypeChecker checker) {
    super(checker);
  }

  @Override
  protected boolean shouldCheckThrowsUOE() {
    // When running under ModifiabilityChecker, GrowChecker handles @ThrowsUOE reporting.
    return false;
  }
}
