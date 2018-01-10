package org.checkerframework.framework.util.typeinference8.infer;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.bound.Capture;
import org.checkerframework.framework.util.typeinference8.constraint.CheckedExceptionConstraint;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Kind;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Expression;
import org.checkerframework.framework.util.typeinference8.constraint.Typing;
import org.checkerframework.framework.util.typeinference8.resolution.Resolution;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InferenceUtils;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

public class InvocationTypeInference {

    private final ProcessingEnvironment env;
    private final TreePath pathToExpression;

    private final Context context;

    public InvocationTypeInference(AnnotatedTypeFactory factory, TreePath pathToExpression) {
        this.env = factory.getProcessingEnv();
        this.pathToExpression = pathToExpression;
        this.context = new Context(env, factory, pathToExpression, this);
    }

    public List<Variable> infer(MethodInvocationTree methodInvocation) {
        Tree assignmentContext = TreeUtils.getAssignmentContext(pathToExpression);
        if (!shouldTryInference(assignmentContext, pathToExpression)) {
            return null;
        }
        ProperType targetType = null;
        TypeMirror assignmentType = InferenceUtils.getTargetType(pathToExpression, context);

        if (assignmentType != null) {
            targetType = new ProperType(assignmentType, context);
        }

        List<Variable> result;
        try {
            result = infer(methodInvocation, targetType);
        } catch (java.lang.Exception ex) {
            logException(methodInvocation, ex);
            return null;
        }
        ExecutableType methodType =
                InternalInferenceUtils.getTypeOfMethodAdaptedToUse(methodInvocation, context);
        checkResult(result, methodInvocation, methodType);
        return result;
    }

    private void logException(MethodInvocationTree methodInvocation, java.lang.Exception ex) {
        SourceChecker checker = context.factory.getContext().getChecker();
        StringBuilder message = new StringBuilder();
        message.append(ex.getLocalizedMessage());
        if (checker.hasOption("printErrorStack")) {
            message.append("\n").append(formatStackTrace(ex.getStackTrace()));
        }
        checker.report(
                Result.failure("type.inference.crash", message.toString()), methodInvocation);
    }

    /** Format a list of {@link StackTraceElement}s to be printed out as an error message. */
    protected String formatStackTrace(StackTraceElement[] stackTrace) {
        boolean first = true;
        StringBuilder sb = new StringBuilder();
        if (stackTrace.length == 0) {
            sb.append("no stack trace available.");
        } else {
            sb.append("Stack trace: ");
        }
        for (StackTraceElement ste : stackTrace) {
            if (!first) {
                sb.append("\n");
            }
            first = false;
            sb.append(ste.toString());
        }
        return sb.toString();
    }

    private boolean shouldTryInference(Tree assignedTo, TreePath path) {
        if (path.getParentPath().getLeaf().getKind() == Tree.Kind.LAMBDA_EXPRESSION) {
            return false;
        }
        if (assignedTo == null) {
            return true;
        }
        switch (assignedTo.getKind()) {
            case RETURN:
                HashSet<Tree.Kind> kinds =
                        new HashSet<>(Arrays.asList(Tree.Kind.LAMBDA_EXPRESSION, Tree.Kind.METHOD));
                Tree enclosing = TreeUtils.enclosingOfKind(path, kinds);
                return enclosing.getKind() != Tree.Kind.LAMBDA_EXPRESSION;
            case METHOD_INVOCATION:
                MethodInvocationTree methodInvocationTree = (MethodInvocationTree) assignedTo;
                if (methodInvocationTree.getTypeArguments().isEmpty()) {
                    ExecutableElement ele = TreeUtils.elementFromUse(methodInvocationTree);
                    return ele.getTypeParameters().isEmpty();
                }
                return false;
            default:
                return !(assignedTo instanceof ExpressionTree
                        && InternalInferenceUtils.isPolyExpression((ExpressionTree) assignedTo));
        }
    }

    private void checkResult(
            List<Variable> result,
            MethodInvocationTree methodInvocation,
            ExecutableType methodType) {
        Map<TypeVariable, TypeMirror> fromReturn =
                InferenceUtils.getMappingFromReturnType(methodInvocation, methodType, context.env);
        for (Variable variable : result) {
            if (!variable.getInvocation().equals(methodInvocation)) {
                continue;
            }
            TypeVariable typeVariable = variable.getJavaType();
            if (fromReturn.containsKey(typeVariable)) {
                TypeMirror correctType = fromReturn.get(typeVariable);
                TypeMirror inferredType = variable.getInstantiation().getJavaType();
                correctType = upperBound(correctType);
                inferredType = upperBound(inferredType);
                if (context.types.isSameType(
                        context.types.erasure((Type) correctType),
                        context.types.erasure((Type) inferredType),
                        false)) {
                    if (sameSame(correctType, inferredType)) {
                        continue;
                    }
                }
                if (!context.types.isSameType((Type) correctType, (Type) inferredType, false)) {
                    // type.inference.not.same=type variable: %s\ninferred: %s\njava type: %s
                    context.factory
                            .getContext()
                            .getChecker()
                            .report(
                                    Result.failure(
                                            "type.inference.not.same",
                                            typeVariable + "(" + variable + ")",
                                            inferredType,
                                            correctType),
                                    methodInvocation);
                }
            }
        }
    }

    private TypeMirror upperBound(TypeMirror type) {
        //        if (InternalInferenceUtils.isCaptured(type)) {
        //            if (((TypeVariable) type).getLowerBound().getKind() != TypeKind.NULL) {
        //                return ((TypeVariable) type).getLowerBound();
        //            } else {
        //                return ((TypeVariable) type).getUpperBound();
        //            }
        //        } else if (type.getKind() == TypeKind.WILDCARD) {
        //            if (((WildcardType) type).getSuperBound() != null) {
        //                return ((WildcardType) type).getSuperBound();
        //            } else if (((WildcardType) type).getExtendsBound() != null) {
        //                return ((WildcardType) type).getExtendsBound();
        //            } else {
        //                return context.object.getJavaType();
        //            }
        //        }
        return type;
    }

    private boolean sameSame(TypeMirror actual, TypeMirror inferred) {
        if (TypesUtils.isCaptured(actual) && TypesUtils.isCaptured(inferred)) {
            if (context.types.isSameWildcard(
                    (WildcardType) TypesUtils.getCapturedWildcard((TypeVariable) actual),
                    (Type) TypesUtils.getCapturedWildcard((TypeVariable) inferred))) {
                return true;
            }
        } else if (TypesUtils.isCaptured(actual) && inferred.getKind() == TypeKind.WILDCARD) {
            if (context.types.isSameWildcard(
                    (WildcardType) TypesUtils.getCapturedWildcard((TypeVariable) actual),
                    (Type) inferred)) {
                return true;
            }
        } else if (actual.getKind() == TypeKind.DECLARED
                && inferred.getKind() == TypeKind.DECLARED) {
            DeclaredType actualDT = (DeclaredType) actual;
            DeclaredType inferredDT = (DeclaredType) inferred;
            if (actualDT.getTypeArguments().size() == inferredDT.getTypeArguments().size()) {
                for (int i = 0; i < actualDT.getTypeArguments().size(); i++) {
                    if (!sameSame(
                            actualDT.getTypeArguments().get(i),
                            inferredDT.getTypeArguments().get(i))) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /** @param target Nullable if methodInvocation isn't assigned. */
    public List<Variable> infer(MethodInvocationTree methodInvocation, ProperType target) {
        ExecutableType methodType =
                InternalInferenceUtils.getTypeOfMethodAdaptedToUse(methodInvocation, context);
        Theta map = Theta.theta(methodInvocation, methodType, context);
        BoundSet b2 = createB2(methodInvocation, methodType, methodInvocation.getArguments(), map);
        BoundSet b3;
        if (target != null && InternalInferenceUtils.isPolyExpression(methodInvocation)) {
            b3 = createB3(b2, methodInvocation, methodType, target, map);
        } else {
            b3 = b2;
        }
        ConstraintSet c =
                createC(methodInvocation, methodType, methodInvocation.getArguments(), map);

        BoundSet b4 = getB4(b3, c);
        List<Variable> thetaPrime = b4.resolve();

        if (b4.isUncheckedConversion()) {
            // If unchecked conversion was necessary for the method to be applicable during
            // constraint set reduction in 18.5.1, then the parameter types of the invocation type
            // of m are obtained by applying theta' to the parameter types of m's type, and the return
            // type and thrown types of the invocation type of m are given by the erasure of the
            // return type and thrown types of m's type.
        }
        return thetaPrime;
    }

    /**
     * Creates the bound set used to determine whether a method is applicable. (See JLS 18.5.1) It
     * does this by: 1. Creates the variables to be solved and add bounds from the type parameter
     * declaration. 2. Adds any bounds implied by the throws clause of {@code methodType}. 3.
     * Constructs constraints between formal parameters and arguments that are pertinent to
     * applicable in {@code invocation}. 4. Reduces and incorporates those constraints which
     * produces B2
     *
     * <p>Generally, all arguments are applicable except: inexact method reference, implicitly typed
     * lambdas, or explicitly type lambda whose return expression(s) are not pertinent. See
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.2.2
     *
     * @param invocation method or constructor invocation
     * @param methodType the type of the method or constructor invoked by expression
     * @param args argument expression tress
     * @param map map of type variables to (inference) variables
     * @return bound set used to determine whether a method is applicable.
     */
    public BoundSet createB2(
            ExpressionTree invocation,
            ExecutableType methodType,
            List<? extends ExpressionTree> args,
            Theta map) {
        BoundSet b0 = BoundSet.initialBounds(map, context);

        // For all i (1 <= i <= p), if Pi appears in the throws clause of m, then the bound throws
        // alphai is implied. These bounds, if any, are incorporated with B0 to produce a new bound set, B1.
        for (TypeMirror type : methodType.getThrownTypes()) {
            AbstractType thrownType = InferenceType.create(type, map, context);
            if (thrownType.isVariable()) {
                ((Variable) thrownType).setHasThrowsBound(true);
            }
        }

        BoundSet b1 = b0;
        ConstraintSet c = new ConstraintSet();
        List<AbstractType> formals = getFormals(invocation, methodType, map, args.size());

        for (int i = 0; i < formals.size(); i++) {
            ExpressionTree ei = args.get(i);
            AbstractType fi = formals.get(i);

            if (!notPertinentToApplicability(ei, fi.isVariable())) {
                c.add(new Expression(ei, fi));
            }
        }

        BoundSet newBounds = c.reduce(context);
        assert !newBounds.containsFalse();
        b1.incorporateToFixedPoint(newBounds);

        return b1;
    }

    public BoundSet createB2MethodRef(
            MemberReferenceTree expression,
            ExecutableType methodType,
            List<AbstractType> args,
            Theta map) {
        BoundSet b0 = BoundSet.initialBounds(map, context);

        // For all i (1 <= i <= p), if Pi appears in the throws clause of m, then the bound throws
        // alphai is implied. These bounds, if any, are incorporated with B0 to produce a new bound set, B1.
        for (TypeMirror type : methodType.getThrownTypes()) {
            AbstractType thrownType = InferenceType.create(type, map, context);
            if (thrownType.isVariable()) {
                ((Variable) thrownType).setHasThrowsBound(true);
            }
        }

        BoundSet b1 = b0;
        ConstraintSet c = new ConstraintSet();
        List<AbstractType> formals = getFormals(expression, methodType, map, args.size());

        for (int i = 0; i < formals.size(); i++) {
            AbstractType ei = args.get(i);
            AbstractType fi = formals.get(i);
            c.add(new Typing(ei, fi, Kind.TYPE_COMPATIBILITY));
        }

        BoundSet newBounds = c.reduce(context);
        assert !newBounds.containsFalse();
        b1.incorporateToFixedPoint(newBounds);

        return b1;
    }

    private List<AbstractType> getFormals(
            ExpressionTree expression, ExecutableType executableType, Theta map, int size) {
        List<TypeMirror> params = new ArrayList<>(executableType.getParameterTypes());

        boolean isVarArg = InternalInferenceUtils.isVarArgMethodCall(expression);
        if (isVarArg) {
            ArrayType vararg = (ArrayType) params.remove(params.size() - 1);
            for (int i = params.size(); i < size; i++) {
                params.add(vararg.getComponentType());
            }
        }
        return InferenceType.create(params, map, context);
    }

    private BoundSet getB4(BoundSet current, ConstraintSet c) {
        while (!c.isEmpty()) {
            ConstraintSet subset = c.getMagicalSubSet(current.getDependencies(c));
            List<Variable> alphas = subset.getAllInputVariables();
            if (!alphas.isEmpty()) {
                BoundSet resolved = Resolution.resolve(alphas, current, context);
                c.applyInstantiations(resolved.getInstantiations(alphas));
            }
            c.remove(subset);
            BoundSet newBounds = subset.reduce(context);
            current.incorporateToFixedPoint(newBounds);
        }
        return current;
    }

    /**
     * Creates constraints against the targets type of {@code invocation} and then reduces and
     * incorporates those constraints with {@code b2}. (See JLS 18.5.2.1)
     *
     * @param b2 BoundSet created by {@link #createB2(ExpressionTree, ExecutableType, List, Theta)}
     * @param invocation method or constructor invocation
     * @param methodType the type of the method or constructor invoked by expression
     * @param target target type of the invocation
     * @param map map of type variables to (inference) variables
     * @return bound set created by constraints against the target type of the invocation
     */
    public BoundSet createB3(
            BoundSet b2,
            ExpressionTree invocation,
            ExecutableType methodType,
            AbstractType target,
            Theta map) {
        AbstractType r;
        if (invocation.getKind() == Tree.Kind.METHOD_INVOCATION
                || invocation.getKind() == Tree.Kind.MEMBER_REFERENCE) {
            r = InferenceType.create(methodType.getReturnType(), map, context);
        } else {
            r = InferenceType.create(TreeUtils.typeOf(invocation), map, context);
        }

        if (b2.isUncheckedConversion()) {
            // If unchecked conversion was necessary for the method to be applicable during
            // constraint set reduction in 18.5.1, the constraint formula <|R| -> T> is reduced and
            // incorporated with B2.
            BoundSet b =
                    new ConstraintSet(new Typing(r.getErased(), target, Kind.TYPE_COMPATIBILITY))
                            .reduce(context);
            b2.incorporateToFixedPoint(b);
            return b2;

        } else if (r.isWildcardParameterizedType()) {
            // Otherwise, if R theta is a parameterized type, G<A1, ..., An>, and one of A1, ...,
            // An is a wildcard, then, for fresh inference variables B1, ..., Bn, the constraint
            // formula <G<B1, ..., Bn> -> T> is reduced and incorporated, along with the bound
            // G<B1, ..., Bn> = capture(G<A1, ..., An>), with B2.
            Capture capture = new Capture(r, invocation, context);
            BoundSet b = capture.incorporate(target, context);
            b2.incorporateToFixedPoint(b);
            return b2;
        } else if (r.isVariable()) {
            Variable alpha = (Variable) r;
            boolean compatiblity = false;
            // T is a reference type, but is not a wildcard-parameterized type, and either
            if (!target.isWildcardParameterizedType()) {
                // i) B2 contains a bound of one of the forms alpha = S or S <: alpha, where S is a wildcard-parameterized type, or
                compatiblity = alpha.hasWildcardParameterizedLowerOrEqualBound();
                if (!compatiblity) {
                    // ii) B2 contains two bounds of the forms S1 <: alpha and S2 <: alpha, where S1 and S2
                    // have supertypes that are two different parameterizations of the same generic class or interface.
                    compatiblity = alpha.hasLowerBoundDifferentParam();
                }
            }
            if (target.isParameterizedType()) {
                // T is a parameterization of a generic class or interface, G, and B2 contains a
                // bound of one of the forms alpha = S or S <: alpha, where there exists no type of the form
                // G<...> that is a supertype of S, but the raw type |G<...>| is a supertype of S.
                compatiblity = alpha.hasRawTypeLowerOrEqualBound(target);
            }
            if (target.getTypeKind().isPrimitive()) {
                // T is a primitive type, and one of the primitive wrapper classes mentioned in 5.1.7
                // is an instantiation, upper bound, or lower bound for alpha in B2.
                compatiblity = alpha.hasPrimitiveWrapperBound();
            }
            if (compatiblity) {
                BoundSet resolve = Resolution.resolve(alpha, b2, context);
                ProperType u = (ProperType) alpha.getInstantiation().capture();
                ConstraintSet constraintSet =
                        new ConstraintSet(new Typing(u, target, Kind.TYPE_COMPATIBILITY));
                BoundSet newBounds = constraintSet.reduce(context);
                resolve.incorporateToFixedPoint(newBounds);
                return resolve;
            }
        }
        ConstraintSet constraintSet =
                new ConstraintSet(new Typing(r, target, Kind.TYPE_COMPATIBILITY));
        BoundSet newBounds = constraintSet.reduce(context);
        b2.incorporateToFixedPoint(newBounds);
        return b2;
    }

    /**
     * Creates the constraints between the formal parameters and arguments that are not pertinent to
     * applicability. (See 18.5.2.2)
     *
     * @param invocation method or constructor invocation
     * @param methodType type of method invoked by {@code invocation}
     * @param args argument expression trees
     * @param map map from type variable to inference variable
     * @return the constraints between the formal parameters and arguments that are not pertinent to
     *     applicability
     */
    private ConstraintSet createC(
            ExpressionTree invocation,
            ExecutableType methodType,
            List<? extends ExpressionTree> args,
            Theta map) {
        ConstraintSet c = new ConstraintSet();
        List<AbstractType> formals = getFormals(invocation, methodType, map, args.size());

        for (int i = 0; i < formals.size(); i++) {
            ExpressionTree ei = args.get(i);
            AbstractType fi = formals.get(i);
            if (notPertinentToApplicability(ei, fi.isVariable())) {
                c.add(new Expression(ei, fi));
            }
            c.addAll(createAddtionalArgConstraints(ei, fi, map));
        }

        return c;
    }

    private ConstraintSet createAddtionalArgConstraints(
            ExpressionTree ei, AbstractType fi, Theta map) {
        ConstraintSet c = new ConstraintSet();

        switch (ei.getKind()) {
            case MEMBER_REFERENCE:
                c.add(new CheckedExceptionConstraint(ei, fi, map));
                break;
            case LAMBDA_EXPRESSION:
                c.add(new CheckedExceptionConstraint(ei, fi, map));
                LambdaExpressionTree lambda = (LambdaExpressionTree) ei;
                for (ExpressionTree expression : TreeUtils.getReturnedExpressions(lambda)) {
                    c.addAll(createAddtionalArgConstraints(expression, fi, map));
                }
                break;
            case METHOD_INVOCATION:
                if (InternalInferenceUtils.isPolyExpression(ei)) {
                    MethodInvocationTree methodInvocation = (MethodInvocationTree) ei;
                    ExecutableType methodType =
                            InternalInferenceUtils.getTypeOfMethodAdaptedToUse(
                                    methodInvocation, context);
                    Theta newMap = Theta.theta(methodInvocation, methodType, context);
                    c.addAll(
                            createC(
                                    methodInvocation,
                                    methodType,
                                    methodInvocation.getArguments(),
                                    newMap));
                }
                break;
            case NEW_CLASS:
                if (InternalInferenceUtils.isPolyExpression(ei)) {
                    NewClassTree newClassTree = (NewClassTree) ei;
                    ExecutableType methodType =
                            InternalInferenceUtils.getTypeOfMethodAdaptedToUse(
                                    newClassTree, context);
                    Theta newMap = Theta.theta(newClassTree, methodType, context);
                    c.addAll(
                            createC(newClassTree, methodType, newClassTree.getArguments(), newMap));
                }
                break;
            case PARENTHESIZED:
                c.addAll(createAddtionalArgConstraints(TreeUtils.skipParens(ei), fi, map));
                break;
            case CONDITIONAL_EXPRESSION:
                ConditionalExpressionTree conditional = (ConditionalExpressionTree) ei;
                c.addAll(createAddtionalArgConstraints(conditional.getTrueExpression(), fi, map));
                c.addAll(createAddtionalArgConstraints(conditional.getFalseExpression(), fi, map));
                break;
            default:
                // no constraints
        }

        return c;
    }

    /**
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.2.2 (Assuming the
     * method is a generic method and the method invocation does not provide explicit type
     * arguments)
     *
     * @param expressionTree expression tree
     * @param isTargetVariable whether the corresponding target type (as derived from the signature
     *     of m) is a type parameter of m
     * @return whether or not {@code expressionTree} is pertinent to applicability
     */
    private boolean notPertinentToApplicability(
            ExpressionTree expressionTree, boolean isTargetVariable) {
        switch (expressionTree.getKind()) {
            case LAMBDA_EXPRESSION:
                LambdaExpressionTree lambda = (LambdaExpressionTree) expressionTree;
                if (TreeUtils.isImplicitlyTypeLambda(lambda) || isTargetVariable) {
                    // An implicitly typed lambda expression.
                    return true;
                } else {
                    // An explicitly typed lambda expression whose body is a block,
                    // where at least one result expression is not pertinent to applicability.
                    // An explicitly typed lambda expression whose body is an expression that is
                    // not pertinent to applicability.
                    for (ExpressionTree result : TreeUtils.getReturnedExpressions(lambda)) {
                        if (notPertinentToApplicability(result, isTargetVariable)) {
                            return true;
                        }
                    }
                    return false;
                }
            case MEMBER_REFERENCE:
                // An inexact method reference expression.
                return isTargetVariable
                        || !TreeUtils.isExactMethodReference((MemberReferenceTree) expressionTree);
            case PARENTHESIZED:
                // A parenthesized expression whose contained expression is not pertinent to
                // applicability.
                return notPertinentToApplicability(
                        TreeUtils.skipParens(expressionTree), isTargetVariable);
            case CONDITIONAL_EXPRESSION:
                ConditionalExpressionTree conditional = (ConditionalExpressionTree) expressionTree;
                // A conditional expression whose second or third operand is not pertinent to
                // applicability.
                return notPertinentToApplicability(
                                conditional.getTrueExpression(), isTargetVariable)
                        || notPertinentToApplicability(
                                conditional.getFalseExpression(), isTargetVariable);
            default:
                return false;
        }
    }
}
