package org.checkerframework.checker.modifiability.shrink;

import org.checkerframework.checker.modifiability.ModifiabilityBaseVisitor;
import org.checkerframework.common.basetype.BaseTypeChecker;

/** Visitor for the {@link ShrinkChecker}. */
public class ShrinkVisitor extends ModifiabilityBaseVisitor {

  /**
   * Create a visitor for the Shrink Checker.
   *
   * @param checker the Shrink Checker
   */
  public ShrinkVisitor(BaseTypeChecker checker) {
    super(checker);
  }

  @Override
  protected boolean shouldCheckModifiabilityAnnotationValidity() {
    // When running under ModifiabilityChecker, GrowChecker handles shared annotation diagnostics.
    return checker.getParentChecker() == null;
  }
}
