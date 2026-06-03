package org.checkerframework.checker.modifiability.shrink;

import org.checkerframework.checker.modifiability.ModifiabilityVisitor;
import org.checkerframework.common.basetype.BaseTypeChecker;

/** Visitor for the {@link ShrinkChecker}. */
public class ShrinkVisitor extends ModifiabilityVisitor {

  /**
   * Create a visitor for the Shrink Checker.
   *
   * @param checker the Shrink Checker
   */
  public ShrinkVisitor(BaseTypeChecker checker) {
    super(checker);
  }

  @Override
  protected boolean shouldCheckUnmodifiableParamLocation() {
    // When running under ModifiabilityChecker, GrowChecker handles @UnmodifiableParam location
    // reporting.
    return checker.getParentChecker() == null;
  }

  @Override
  protected boolean shouldCheckCustomModifiabilityAnnotation() {
    // When running under ModifiabilityChecker, GrowChecker handles custom type warnings.
    return checker.getParentChecker() == null;
  }
}
