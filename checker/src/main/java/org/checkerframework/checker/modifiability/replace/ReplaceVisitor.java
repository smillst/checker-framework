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
}
