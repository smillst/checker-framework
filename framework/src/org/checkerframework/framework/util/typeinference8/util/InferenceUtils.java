package org.checkerframework.framework.util.typeinference8.util;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.TreeUtils;

public class InferenceUtils {

    /**
     * Returns the tree with the assignment context for the treePath leaf node. (Does not handle
     * pseudo-assignment of an argument to a parameter or a receiver expression to a receiver.)
     *
     * <p>The assignment context for the {@code treePath} is the leaf of its parent, if the parent
     * is one of the following trees:
     *
     * <ul>
     *   <li>AssignmentTree
     *   <li>CompoundAssignmentTree
     *   <li>MethodInvocationTree
     *   <li>NewArrayTree
     *   <li>NewClassTree
     *   <li>ReturnTree
     *   <li>VariableTree
     * </ul>
     *
     * If the parent is a ConditionalExpressionTree we need to distinguish two cases: If the leaf is
     * either the then or else branch of the ConditionalExpressionTree, then recurse on the parent.
     * If the leaf is the condition of the ConditionalExpressionTree, then return null to not
     * consider this assignment context.
     *
     * <p>If the leaf is a ParenthesizedTree, then recurse on the parent.
     *
     * <p>Otherwise, null is returned.
     *
     * @return the assignment context as described
     */
    public static Tree getAssignmentContext(final TreePath treePath) {
        TreePath parentPath = treePath.getParentPath();

        if (parentPath == null) {
            return null;
        }

        Tree parent = parentPath.getLeaf();
        switch (parent.getKind()) {
            case PARENTHESIZED:
                return getAssignmentContext(parentPath);
            case CONDITIONAL_EXPRESSION:
                ConditionalExpressionTree cet = (ConditionalExpressionTree) parent;
                if (cet.getCondition() == treePath.getLeaf()) {
                    // The assignment context for the condition is simply boolean.
                    // No point in going on.
                    return null;
                }
                // Otherwise use the context of the ConditionalExpressionTree.
                return getAssignmentContext(parentPath);
            case ASSIGNMENT:
            case METHOD_INVOCATION:
            case NEW_ARRAY:
            case NEW_CLASS:
            case RETURN:
            case VARIABLE:
                return parent;
            default:
                // 11 Tree.Kinds are CompoundAssignmentTrees,
                // so use instanceof rather than listing all 11.
                if (parent instanceof CompoundAssignmentTree) {
                    return parent;
                }
                return null;
        }
    }

    /**
     * Returns the annotated type that the leaf of path is assigned to, if it is within an
     * assignment context. Returns the annotated type that the method invocation at the leaf is
     * assigned to. If the result is a primitive, return the boxed version.
     *
     * @return type that path leaf is assigned to
     */
    public static TypeMirror assignedTo(TreePath path) {
        Tree assignmentContext = TreeUtils.getAssignmentContext(path);
        if (assignmentContext == null) {
            return null;
        }

        switch (assignmentContext.getKind()) {
            case ASSIGNMENT:
                ExpressionTree variable = ((AssignmentTree) assignmentContext).getVariable();
                return InternalUtils.typeOf(variable);
            case VARIABLE:
                VariableTree variableTree = (VariableTree) assignmentContext;
                return InternalUtils.typeOf(variableTree.getType());
            case METHOD_INVOCATION:
                MethodInvocationTree methodInvocation = (MethodInvocationTree) assignmentContext;
                ExecutableElement methodElt = TreeUtils.elementFromUse(methodInvocation);
                return assignedToExecutable(path, methodElt, methodInvocation.getArguments());
            case NEW_CLASS:
                NewClassTree newClassTree = (NewClassTree) assignmentContext;
                ExecutableElement constructorElt = InternalUtils.constructor(newClassTree);
                return assignedToExecutable(path, constructorElt, newClassTree.getArguments());
            case NEW_ARRAY:
                throw new RuntimeException("Not implement");
            case RETURN:
                HashSet<Kind> kinds =
                        new HashSet<>(Arrays.asList(Kind.LAMBDA_EXPRESSION, Kind.METHOD));
                Tree enclosing = TreeUtils.enclosingOfKind(path, kinds);
                if (enclosing.getKind() == Kind.METHOD) {
                    MethodTree methodTree = (MethodTree) enclosing;
                    return InternalUtils.typeOf(methodTree.getReturnType());
                } else {
                    // TODO: I don't think this should happen. during inference
                    LambdaExpressionTree lambdaTree = (LambdaExpressionTree) enclosing;
                    return InternalUtils.typeOf(lambdaTree);
                }
            default:
                if (assignmentContext
                        .getKind()
                        .asInterface()
                        .equals(CompoundAssignmentTree.class)) {
                    // 11 Tree kinds are compound assignments, so don't use it in the switch
                    ExpressionTree var = ((CompoundAssignmentTree) assignmentContext).getVariable();
                    return InternalUtils.typeOf(var);
                } else {
                    ErrorReporter.errorAbort(
                            "Unexpected assignment context.\nKind: %s\nTree: %s",
                            assignmentContext.getKind(), assignmentContext);
                    return null;
                }
        }
    }

    private static TypeMirror assignedToExecutable(
            TreePath path, ExecutableElement methodElt, List<? extends ExpressionTree> arguments) {

        int treeIndex = -1;
        for (int i = 0; i < arguments.size(); ++i) {
            ExpressionTree argumentTree = arguments.get(i);
            if (isArgument(path, argumentTree)) {
                treeIndex = i;
                break;
            }
        }

        assert treeIndex != -1
                : "Could not find path in MethodInvocationTree.\n" + "treePath=" + path.toString();

        if (treeIndex >= methodElt.getParameters().size() && methodElt.isVarArgs()) {
            treeIndex = methodElt.getParameters().size() - 1;
        }

        VariableElement paramElement = methodElt.getParameters().get(treeIndex);
        return ElementUtils.getType(paramElement);
    }

    /**
     * Returns whether argumentTree is the tree at the leaf of path. if tree is a conditional
     * expression, isArgument is called recursively on the true and false expressions.
     */
    private static boolean isArgument(TreePath path, ExpressionTree argumentTree) {
        argumentTree = TreeUtils.skipParens(argumentTree);
        if (argumentTree == path.getLeaf()) {
            return true;
        } else if (argumentTree.getKind() == Kind.CONDITIONAL_EXPRESSION) {
            ConditionalExpressionTree conditionalExpressionTree =
                    (ConditionalExpressionTree) argumentTree;
            return isArgument(path, conditionalExpressionTree.getTrueExpression())
                    || isArgument(path, conditionalExpressionTree.getFalseExpression());
        }
        return false;
    }

    /**
     * If the variable's type is a type variable, return getAnnotatedTypeLhsNoTypeVarDefault(tree).
     * Rational:
     *
     * <p>For example:
     *
     * <pre>{@code
     * <S> S bar () {...}
     *
     * <T> T foo(T p) {
     *     T local = bar();
     *     return local;
     *   }
     * }</pre>
     *
     * During type argument inference of {@code bar}, the assignment context is {@code local}. If
     * the local variable default is used, then the type of assignment context type is
     * {@code @Nullable T} and the type argument inferred for {@code bar()} is {@code @Nullable T}.
     * And an incompatible types in return error is issued.
     *
     * <p>If instead, the local variable default is not applied, then the assignment context type is
     * {@code T} (with lower bound {@code @NonNull Void} and upper bound {@code @Nullable Object})
     * and the type argument inferred for {@code bar()} is {@code T}. During dataflow, the type of
     * {@code local} is refined to {@code T} and the return is legal.
     *
     * <p>If the assignment context type was a declared type, for example:
     *
     * <pre>{@code
     * <S> S bar () {...}
     * Object foo() {
     *     Object local = bar();
     *     return local;
     * }
     * }</pre>
     *
     * The local variable default must be used or else the assignment context type is missing an
     * annotation. So, an incompatible types in return error is issued in the above code. We could
     * improve type argument inference in this case and by using the lower bound of {@code S}
     * instead of the local variable default.
     *
     * @param atypeFactory AnnotatedTypeFactory
     * @param assignmentContext VariableTree
     * @return AnnotatedTypeMirror of Assignment context
     */
    public static AnnotatedTypeMirror assignedToVariable(
            AnnotatedTypeFactory atypeFactory, Tree assignmentContext) {
        if (atypeFactory instanceof GenericAnnotatedTypeFactory<?, ?, ?, ?>) {
            final GenericAnnotatedTypeFactory<?, ?, ?, ?> gatf =
                    ((GenericAnnotatedTypeFactory<?, ?, ?, ?>) atypeFactory);
            return gatf.getAnnotatedTypeLhsNoTypeVarDefault(assignmentContext);
        } else {
            return atypeFactory.getAnnotatedType(assignmentContext);
        }
    }

    public static boolean isParameterizedType(TypeMirror type) {
        if (type == null || type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) type;
        return !declaredType.getTypeArguments().isEmpty()
                || isParameterizedType(declaredType.getEnclosingType());
    }
}
