package org.checkerframework.checker.modifiability;

import com.sun.source.util.TreePath;
import org.checkerframework.framework.source.SourceVisitor;

/**
 * The visitor for the aggregate {@link ModifiabilityChecker}.
 *
 * <p>The sub-checkers do all the type-checking. This visitor performs only the checks that do not
 * depend on a particular modifiability hierarchy, so that each such check is performed once rather
 * than once per sub-checker. Currently there is one such check: {@code @UnmodifiableParam} may be
 * written only on a formal parameter or a receiver parameter.
 *
 * <p>When a modifiability sub-checker is run on its own rather than under {@link
 * ModifiabilityChecker}, {@link ModifiabilityBaseVisitor} performs the same checks.
 */
public class ModifiabilityVisitor extends SourceVisitor<Void, Void> {

  /** Issues errors about {@code @UnmodifiableParam} annotations in disallowed locations. */
  private final UnmodifiableParamLocationScanner unmodifiableParamLocationScanner;

  /**
   * Creates a ModifiabilityVisitor.
   *
   * @param checker the checker that uses this visitor
   */
  public ModifiabilityVisitor(ModifiabilityChecker checker) {
    super(checker);
    this.unmodifiableParamLocationScanner = new UnmodifiableParamLocationScanner(checker);
  }

  @Override
  public void visit(TreePath path) {
    super.visit(path);
    unmodifiableParamLocationScanner.scan(path.getLeaf(), null);
  }
}
