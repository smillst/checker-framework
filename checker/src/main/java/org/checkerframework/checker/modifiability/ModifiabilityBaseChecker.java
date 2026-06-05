package org.checkerframework.checker.modifiability;

import org.checkerframework.common.basetype.BaseTypeChecker;

/**
 * Base class for the Modifiability sub-checkers.
 *
 * <p>This class exists so {@link
 * org.checkerframework.framework.source.SourceChecker#getMessagesProperties()} finds the shared
 * {@code messages.properties} file in {@code org.checkerframework.checker.modifiability}. The Grow,
 * SeqGrow, Shrink, and Replace checkers all report diagnostics whose message keys are defined
 * there; without this common superclass, those keys would need to be duplicated in each
 * sub-checker's package.
 */
public abstract class ModifiabilityBaseChecker extends BaseTypeChecker {

  /** Creates a new ModifiabilityBaseChecker. */
  protected ModifiabilityBaseChecker() {}

  /**
   * Returns true if this checker should report diagnostics about annotations whose validity is
   * shared across all modifiability sub-checkers.
   *
   * <p>When a sub-checker runs on its own, it should report these diagnostics. When sub-checkers
   * run under the aggregate {@link ModifiabilityChecker}, only {@link GrowChecker} reports them to
   * avoid duplicate messages.
   *
   * @return true if this checker should report shared annotation diagnostics
   */
  protected boolean shouldCheckModifiabilityAnnotationValidity() {
    return getParentChecker() == null || this instanceof GrowChecker;
  }
}
