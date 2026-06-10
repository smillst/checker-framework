package org.checkerframework.checker.modifiability.seqgrow;

import org.checkerframework.checker.modifiability.ModifiabilityBaseVisitor;
import org.checkerframework.common.basetype.BaseTypeChecker;

/** Visitor for the {@link SeqGrowChecker}. */
public class SeqGrowVisitor extends ModifiabilityBaseVisitor {

  /**
   * Creates a visitor for the SeqGrow Checker.
   *
   * @param checker the SeqGrow Checker
   */
  public SeqGrowVisitor(BaseTypeChecker checker) {
    super(checker);
  }

  @Override
  protected boolean shouldCheckModifiabilityAnnotationValidity() {
    // When running under ModifiabilityChecker, GrowChecker handles shared annotation diagnostics.
    return checker.getParentChecker() == null;
  }
}
