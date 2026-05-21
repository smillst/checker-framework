package org.checkerframework.checker.modifiability.replace;

import org.checkerframework.checker.modifiability.ModifiabilityVisitor;
import org.checkerframework.common.basetype.BaseTypeChecker;

/** Visitor for the {@link ReplaceChecker}. */
public class ReplaceVisitor extends ModifiabilityVisitor {

  /**
   * Creates a visitor for the Replace Checker.
   *
   * @param checker the Replace Checker
   */
  public ReplaceVisitor(BaseTypeChecker checker) {
    super(checker);
  }

  @Override
  protected boolean shouldCheckThrowsUOE() {
    // When running under ModifiabilityChecker, GrowChecker handles @ThrowsUOE reporting.
    return checker.getParentChecker() == null;
  }

  @Override
  protected boolean shouldCheckUnmodParamLocation() {
    // When running under ModifiabilityChecker, GrowChecker handles @UnmodParam location reporting.
    return checker.getParentChecker() == null;
  }

  @Override
  protected boolean shouldCheckCustomModifiabilityAnnotation() {
    // When running under ModifiabilityChecker, GrowChecker handles custom type warnings.
    return checker.getParentChecker() == null;
  }
}
