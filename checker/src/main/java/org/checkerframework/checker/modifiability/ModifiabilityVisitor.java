package org.checkerframework.checker.modifiability;

import com.sun.source.tree.MethodInvocationTree;
import javax.lang.model.element.ExecutableElement;
import org.checkerframework.checker.modifiability.qual.ThrowsUOE;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.TreeUtils;

/**
 * Base visitor for the Modifiability sub-checkers (Grow, Shrink, Replace).
 *
 * <p>This class contains logic shared across all three sub-checkers:
 *
 * <ul>
 *   <li>Reporting errors for invocations of methods annotated with {@link ThrowsUOE}.
 *   <li>Suppressing the "constructor result must be TOP" check, since collection constructors may
 *       legitimately produce {@code @Modifiable}.
 * </ul>
 */
public class ModifiabilityVisitor extends BaseTypeVisitor<BaseAnnotatedTypeFactory> {

  /**
   * Create a ModifiabilityVisitor.
   *
   * @param checker the checker that uses this visitor
   */
  public ModifiabilityVisitor(BaseTypeChecker checker) {
    super(checker);
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
    // Methods annotated @ThrowsUOE always throw UnsupportedOperationException when called.
    // When running as part of the aggregate ModifiabilityChecker, only the GrowChecker
    // reports this error (shouldCheckThrowsUOE() returns false in the other two) to avoid
    // tripling the diagnostic. When running a sub-checker standalone, each reports it.
    if (shouldCheckThrowsUOE()) {
      ExecutableElement method = TreeUtils.elementFromUse(node);
      if (atypeFactory.getDeclAnnotation(method, ThrowsUOE.class) != null) {
        checker.reportError(node, "usage.throws.uoe", method.getSimpleName());
      }
    }
    return super.visitMethodInvocation(node, p);
  }

  /**
   * Returns true if this checker should report {@code @ThrowsUOE} errors.
   *
   * <p>The default is {@code true}. {@link
   * org.checkerframework.checker.modifiability.shrink.ShrinkVisitor} and {@link
   * org.checkerframework.checker.modifiability.replace.ReplaceVisitor} override this to return
   * {@code false} when running under the aggregate {@link ModifiabilityChecker}, so that each
   * {@code @ThrowsUOE} call site produces exactly one error rather than three.
   *
   * @return true if this visitor should report {@code @ThrowsUOE} violations
   */
  protected boolean shouldCheckThrowsUOE() {
    return true;
  }

  // Suppresses the framework's "constructor result must be TOP" check.
  // Collection constructors (e.g., new ArrayList()) legitimately produce @Modifiable, which is a
  // subtype of the top type @MaybeModifiable. This suppression is sound: constructors are
  // typed by their stubs or by defaults inferred from the class's annotations, so they cannot
  // silently widen an unmodifiable collection to @Modifiable.
  @Override
  protected void checkConstructorResult(
      AnnotatedExecutableType constructorType, ExecutableElement constructorElement) {}
}
