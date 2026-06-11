package org.checkerframework.checker.modifiability;

import java.util.Set;
import org.checkerframework.checker.modifiability.iterator.IteratorChecker;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.source.SourceChecker;

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

  @Override
  protected Set<Class<? extends SourceChecker>> getImmediateSubcheckerClasses() {
    Set<Class<? extends SourceChecker>> checkers = super.getImmediateSubcheckerClasses();
    if (this instanceof IteratorChecker) {
      return checkers;
    }
    checkers.add(IteratorChecker.class);
    return checkers;
  }
}
