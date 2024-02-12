package org.checkerframework.checker.nonempty;

import java.util.Arrays;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import org.checkerframework.checker.nonempty.qual.NonEmpty;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.node.*;
import org.checkerframework.dataflow.expression.JavaExpression;
import org.checkerframework.dataflow.util.NodeUtils;
import org.checkerframework.framework.flow.CFAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.javacutil.TreeUtils;

/**
 * This class provides methods used by the Non-Empty Checker as transfer functions for type rules
 * that cannot be expressed via simple pre- or post-conditional annotations.
 */
public class NonEmptyTransfer extends CFTransfer {

  /** A {@link ProcessingEnvironment} instance. */
  private final ProcessingEnvironment env;

  /** The {@code size()} method of the {@link java.util.Collection} interface. */
  private final ExecutableElement collectionSize;

  /** The {@code size()} method of the {@link java.util.Map} class. */
  private final ExecutableElement mapSize;

  /** A {@link NonEmptyAnnotatedTypeFactory} instance. */
  private final NonEmptyAnnotatedTypeFactory aTypeFactory;

  public NonEmptyTransfer(CFAnalysis analysis) {
    super(analysis);

    this.env = analysis.getTypeFactory().getProcessingEnv();
    this.collectionSize = TreeUtils.getMethod("java.util.Collection", "size", 0, this.env);
    this.mapSize = TreeUtils.getMethod("java.util.Map", "size", 0, this.env);
    this.aTypeFactory = (NonEmptyAnnotatedTypeFactory) analysis.getTypeFactory();
  }

  @Override
  public TransferResult<CFValue, CFStore> visitEqualTo(
      EqualToNode n, TransferInput<CFValue, CFStore> in) {
    TransferResult<CFValue, CFStore> result = super.visitEqualTo(n, in);
    // Account for the case where the sizes of two containers are compared
    strengthenAnnotationSizeEquals(n.getLeftOperand(), n.getRightOperand(), result.getThenStore());
    // Account for the case where size is checked against a non-zero integer
    refineGTE(n.getLeftOperand(), n.getRightOperand(), result.getThenStore());
    refineGTE(n.getRightOperand(), n.getLeftOperand(), result.getThenStore());
    // A == 0 is the inversion of A != 0
    refineNotEqual(n.getLeftOperand(), n.getRightOperand(), result.getElseStore());
    refineNotEqual(n.getRightOperand(), n.getLeftOperand(), result.getElseStore());
    return result;
  }

  @Override
  public TransferResult<CFValue, CFStore> visitNotEqual(
      NotEqualNode n, TransferInput<CFValue, CFStore> in) {
    TransferResult<CFValue, CFStore> result = super.visitNotEqual(n, in);
    strengthenAnnotationSizeEquals(n.getLeftOperand(), n.getRightOperand(), result.getElseStore());
    refineNotEqual(n.getLeftOperand(), n.getRightOperand(), result.getThenStore());
    refineNotEqual(n.getRightOperand(), n.getLeftOperand(), result.getThenStore());
    return result;
  }

  @Override
  public TransferResult<CFValue, CFStore> visitLessThan(
      LessThanNode n, TransferInput<CFValue, CFStore> in) {
    TransferResult<CFValue, CFStore> result = super.visitLessThan(n, in);

    // A < B is equivalent to B > A
    refineGT(n.getRightOperand(), n.getLeftOperand(), result.getThenStore());
    // This handles the case where n < container.size()
    refineGTE(n.getLeftOperand(), n.getRightOperand(), result.getElseStore());
    return result;
  }

  @Override
  public TransferResult<CFValue, CFStore> visitLessThanOrEqual(
      LessThanOrEqualNode n, TransferInput<CFValue, CFStore> in) {
    TransferResult<CFValue, CFStore> result = super.visitLessThanOrEqual(n, in);

    // A <= B is equivalent to B > A
    refineGT(n.getLeftOperand(), n.getRightOperand(), result.getElseStore());
    // This handles the case where n <= container.size()
    refineGTE(n.getRightOperand(), n.getLeftOperand(), result.getThenStore());
    return result;
  }

  @Override
  public TransferResult<CFValue, CFStore> visitGreaterThan(
      GreaterThanNode n, TransferInput<CFValue, CFStore> in) {
    TransferResult<CFValue, CFStore> result = super.visitGreaterThan(n, in);
    refineGT(n.getLeftOperand(), n.getRightOperand(), result.getThenStore());
    return result;
  }

  @Override
  public TransferResult<CFValue, CFStore> visitGreaterThanOrEqual(
      GreaterThanOrEqualNode n, TransferInput<CFValue, CFStore> in) {
    TransferResult<CFValue, CFStore> result = super.visitGreaterThanOrEqual(n, in);
    refineGTE(n.getLeftOperand(), n.getRightOperand(), result.getThenStore());
    return result;
  }

  @Override
  public TransferResult<CFValue, CFStore> visitCase(
      CaseNode n, TransferInput<CFValue, CFStore> in) {
    TransferResult<CFValue, CFStore> result = super.visitCase(n, in);
    List<Node> caseOperands = n.getCaseOperands();
    AssignmentNode assign = n.getSwitchOperand();
    Node switchNode = assign.getExpression();
    refineSwitchStatement(switchNode, caseOperands, result.getThenStore(), result.getElseStore());
    return result;
  }

  /**
   * Refine the transfer result's store, given the left- and right-hand side of an equality check
   * comparing container sizes.
   *
   * @param lhs a node that may be a method invocation for {@code Collection.size()} or {@code
   *     Map.size()}
   * @param rhs a node that may be a method invocation for {@code Collection.size()} or {@code
   *     Map.size()}
   * @param store the "then" store of the comparison operation
   */
  private void strengthenAnnotationSizeEquals(Node lhs, Node rhs, CFStore store) {
    if (!isSizeAccess(lhs) || !isSizeAccess(rhs)) {
      return;
    }
    @Nullable AnnotationMirror lhsNonEmptyAnno =
        aTypeFactory.getAnnotationFromJavaExpression(
            getReceiver(lhs), lhs.getTree(), NonEmpty.class);
    @Nullable AnnotationMirror rhsNonEmptyAnno =
        aTypeFactory.getAnnotationFromJavaExpression(
            getReceiver(rhs), rhs.getTree(), NonEmpty.class);
    // TODO: use aTypeFactory.getQualifierHierarchy().greatestLowerBoundQualifiersOnly() ?
    if (lhsNonEmptyAnno != null) {
      store.insertValue(getReceiver(rhs), aTypeFactory.NON_EMPTY);
    } else if (rhsNonEmptyAnno != null) {
      store.insertValue(getReceiver(lhs), aTypeFactory.NON_EMPTY);
    }
  }

  /**
   * Updates the transfer result's store with information from the Non-Empty type system for
   * expressions of the form {@code container.size() != n} and {@code n != container.size()}.
   *
   * <p>For example, the type of {@code container} in the "then" branch of a conditional statement
   * with the test {@code container.size() != n} where {@code n} is 0 should refine to
   * {@code @NonEmpty}.
   *
   * <p>This method is also used to refine the "else" store of an equality comparison where {@code
   * container.size()} is compared against 0.
   *
   * @param possibleSizeAccess a node that may be a method invocation for {@code Collection.size()}
   *     or {@code Map.size()}
   * @param possibleIntegerLiteral a node that may be an {@link IntegerLiteralNode}
   * @param store the abstract store to update
   */
  private void refineNotEqual(Node possibleSizeAccess, Node possibleIntegerLiteral, CFStore store) {
    if (!isSizeAccess(possibleSizeAccess)
        || !(possibleIntegerLiteral instanceof IntegerLiteralNode)) {
      return;
    }
    IntegerLiteralNode integerLiteralNode = (IntegerLiteralNode) possibleIntegerLiteral;
    if (integerLiteralNode.getValue() == 0) {
      JavaExpression receiver = getReceiver(possibleSizeAccess);
      store.insertValue(receiver, aTypeFactory.NON_EMPTY);
    }
  }

  /**
   * Updates the transfer result's store with information from the Non-Empty type system for
   * expressions of the form {@code container.size() > n}.
   *
   * <p>For example, the type of {@code container} in the "then" branch of a conditional statement
   * with the test {@code container.size() > n} where {@code n >= 0} should be refined to
   * {@code @NonEmpty}.
   *
   * @param possibleSizeAccess a node that may be a method invocation for {@code Collection.size()}
   *     or {@code Map.size()}
   * @param possibleIntegerLiteral a node that may be an {@link IntegerLiteralNode}
   * @param store the abstract store to update
   */
  private void refineGT(Node possibleSizeAccess, Node possibleIntegerLiteral, CFStore store) {
    if (!isSizeAccess(possibleSizeAccess)
        || !(possibleIntegerLiteral instanceof IntegerLiteralNode)) {
      return;
    }
    IntegerLiteralNode integerLiteralNode = (IntegerLiteralNode) possibleIntegerLiteral;
    if (integerLiteralNode.getValue() >= 0) {
      JavaExpression receiver = getReceiver(possibleSizeAccess);
      store.insertValue(receiver, aTypeFactory.NON_EMPTY);
    }
  }

  /**
   * Updates the transfer result's store with information from the Non-Empty type system for
   * expressions of the form {@code container.size() >= n}.
   *
   * <p>For example, the type of {@code container} in the "then" branch of a conditional statement
   * with the test {@code container.size() >= n} where {@code n > 0} should be refined to
   * {@code @NonEmpty}.
   *
   * <p>This method is also used to refine the "then" branch of an equality comparison where {@code
   * container.size()} is compared against a non-zero value.
   *
   * @param possibleSizeAccess a node that may be a method invocation for {@code Collection.size()}
   *     or {@code Map.size()}
   * @param possibleIntegerLiteral a node that may be an {@link IntegerLiteralNode}
   * @param store the abstract store to update
   */
  private void refineGTE(Node possibleSizeAccess, Node possibleIntegerLiteral, CFStore store) {
    if (!isSizeAccess(possibleSizeAccess)
        || !(possibleIntegerLiteral instanceof IntegerLiteralNode)) {
      return;
    }
    IntegerLiteralNode integerLiteralNode = (IntegerLiteralNode) possibleIntegerLiteral;
    if (integerLiteralNode.getValue() > 0) {
      JavaExpression receiver = getReceiver(possibleSizeAccess);
      store.insertValue(receiver, aTypeFactory.NON_EMPTY);
    }
  }

  /**
   * Updates the transfer result's store with information from the Non-Empty type system for switch
   * statements, where the test expression is of the form {@code container.size()}.
   *
   * <p>For example, the "then" store of any case node with an integer value greater than 0 should
   * refine the type of {@code container} to {@code @NonEmpty}.
   *
   * @param possibleSizeAccess a node that may be a method invocation for {@code Collection.size()}
   *     or {@code Map.size()}
   * @param caseOperands the operands within each case label
   * @param thenStore the "then" store
   * @param elseStore the "else" store, corresponding to the "default" case label
   */
  private void refineSwitchStatement(
      Node possibleSizeAccess, List<Node> caseOperands, CFStore thenStore, CFStore elseStore) {
    if (!isSizeAccess(possibleSizeAccess)) {
      return;
    }
    for (Node caseOperand : caseOperands) {
      if (!(caseOperand instanceof IntegerLiteralNode)) {
        continue;
      }
      IntegerLiteralNode caseIntegerLiteral = (IntegerLiteralNode) caseOperand;
      JavaExpression receiver = getReceiver(possibleSizeAccess);
      // If a value is encountered that is <= 0, the type of the container in the "else" store
      // (i.e., the
      // default case) is refined to @NonEmpty
      CFStore storeToUpdate = caseIntegerLiteral.getValue() > 0 ? thenStore : elseStore;
      storeToUpdate.insertValue(receiver, aTypeFactory.NON_EMPTY);
    }
  }

  /**
   * Return true if the given node is an instance of a method invocation node for {@code
   * Collection.size()} or {@code Map.size()}.
   *
   * @param possibleSizeAccess a node that may be a method call to the {@code size()} method in the
   *     {@link java.util.List} or {@link java.util.Map} types
   * @return true if the node is a method call to size()
   */
  private boolean isSizeAccess(Node possibleSizeAccess) {
    // In Java 9+, use `List.of()`
    List<ExecutableElement> sizeAccessMethods = Arrays.asList(collectionSize, mapSize);
    return sizeAccessMethods.stream()
        .anyMatch(
            sizeAccessMethod ->
                NodeUtils.isMethodInvocation(possibleSizeAccess, sizeAccessMethod, env));
  }

  /**
   * Return the receiver as a {@link JavaExpression} given a method invocation node.
   *
   * @param node an instance of a method access
   * @return the receiver as a {@link JavaExpression}
   */
  private JavaExpression getReceiver(Node node) {
    MethodAccessNode methodAccessNode = ((MethodInvocationNode) node).getTarget();
    return JavaExpression.fromNode(methodAccessNode.getReceiver());
  }
}