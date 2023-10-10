package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.framework.util.typeinference8.util.Java8InferenceContext;

public class QualifierVar extends AbstractQualifier {
  /** Identification number. Used only to make debugging easier. */
  protected final int id;

  /**
   * The expression for which this variable is being solved. Used to differentiate qualifier
   * variables for two different invocations of the same method.
   */
  protected final ExpressionTree invocation;

  /** The polymorphic qualifier associated with this var. */
  protected final AnnotationMirror polyQualifier;

  /**
   * Creates a {@link QualifierVar}.
   *
   * @param invocation the expression for which this variable is being solved
   * @param polyQualifier polymorphic qualifier associated with this var
   * @param context the context
   */
  public QualifierVar(
      ExpressionTree invocation, AnnotationMirror polyQualifier, Java8InferenceContext context) {
    this.id = context.getNextVariableId();
    this.invocation = invocation;
    this.polyQualifier = polyQualifier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    QualifierVar that = (QualifierVar) o;
    return id == that.id
        && Objects.equals(invocation, that.invocation)
        && Objects.equals(polyQualifier, that.polyQualifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, invocation, polyQualifier);
  }

  @Override
  public String toString() {
    return "@P" + id;
  }
}
