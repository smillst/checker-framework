package org.checkerframework.framework.util.typeinference8.reduction;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Expression;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InferenceUtils;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

public class ReduceExpression {
    /** See JLS 18.2.1 */
    public static ReductionResult reduce(Expression constraint, Context context) {
        switch (constraint.getExpressionKind()) {
            case PROPER_TYPE:
                return reduceProperType(constraint);
            case STANDALONE:
                return reduceStandalone(constraint, context);
            case PARENTHESIZED:
                return reduceParenthesized(constraint);
            case METHOD_INVOCATION:
                return reduceMethodInvocation(constraint, context);
            case CONDITIONAL:
                return reduceConditional(constraint);
            case LAMBDA:
                return reduceLambda(
                        constraint.getT(),
                        (LambdaExpressionTree) constraint.getExpression(),
                        context);
            case METHOD_REF:
                return reduceMethodRef(
                        constraint.getT(),
                        (MemberReferenceTree) constraint.getExpression(),
                        context);
            default:
                ErrorReporter.errorAbort("Unexpected ExpressionKind: %s", constraint.getKind());
                BoundSet boundSet = new BoundSet(context);
                boundSet.addFalse();
                return boundSet;
        }
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.1-300 */
    private static ReductionResult reduceMethodRef(
            AbstractType t, MemberReferenceTree memRef, Context context) {
        if (InternalInferenceUtils.isExact(memRef)) {
            ExecutableType typeOfPoAppMethod =
                    TypesUtils.findFunctionType(TreeUtils.typeOf(memRef), context.env);

            ConstraintSet constraintSet = new ConstraintSet();
            List<AbstractType> ps = t.getFunctionTypeParameters();
            List<AbstractType> fs = new ArrayList<>();
            for (TypeMirror param : typeOfPoAppMethod.getParameterTypes()) {
                fs.add(new ProperType(param, context));
            }

            if (ps.size() == fs.size() + 1) {
                AbstractType targetReference = ps.remove(0);
                ProperType referenceType =
                        new ProperType(TreeUtils.typeOf(memRef.getQualifierExpression()), context);
                constraintSet.add(
                        new Typing(targetReference, referenceType, Constraint.Kind.SUBTYPE));
            }
            for (int i = 0; i < ps.size(); i++) {
                constraintSet.add(new Typing(ps.get(i), fs.get(i), Constraint.Kind.SUBTYPE));
            }
            AbstractType r = t.getFunctionTypeReturn();
            if (r != null && r.getTypeKind() != TypeKind.VOID) {
                AbstractType rPrime = new ProperType(typeOfPoAppMethod.getReturnType(), context);
                constraintSet.add(new Typing(rPrime, r, Constraint.Kind.TYPE_COMPATIBILITY));
            }
            return constraintSet;
        }
        // else

        if (memRef.getTypeArguments() == null && InternalInferenceUtils.isGenericMethod(memRef)) {
            // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.1-300-D-B-BC
            // Otherwise, if the method reference expression elides TypeArguments, and the
            // compile-time declaration is a generic method, and the return type of the
            // compile-time declaration mentions at least one of the method's type parameters,
            // then
            // the constraint reduces to the bound set B3 which would be used to determine the
            // method reference's invocation type when targeting the return type of the function
            // type, as defined in 18.5.2. B3 may contain new inference variables, as well as
            // dependencies between these new variables and the inference variables in T.

            // compile-time declaration and invocation: ((JCMemberReference)memRef).sym
            // function type =     type(InternalUtils.findFunction());

        } else {
            // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.1-300-D-B-C
            // Otherwise, let R be the return type of the function type, and let R' be the result
            // of applying capture conversion (§5.1.10) to the return type of the invocation type
            // (§15.12.2.6) of the compile-time declaration. If R' is void, the constraint reduces
            // to false; otherwise, the constraint reduces to ‹R' → R›.
        }

        // TODO: implement
        throw new RuntimeException("Not implemented");
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.1-200 */
    private static ReductionResult reduceLambda(
            AbstractType t, LambdaExpressionTree lambda, Context context) {
        AbstractType tPrime = getGroundTargetType(t, lambda, context);
        ConstraintSet constraintSet = new ConstraintSet();

        if (!InternalInferenceUtils.isImplicitlyType(lambda)) {
            // Explicitly typed lambda
            List<? extends VariableTree> parameters = lambda.getParameters();
            List<AbstractType> gs = t.getFunctionTypeParameters();
            assert parameters.size() == gs.size();

            for (int i = 0; i < gs.size(); i++) {
                VariableTree parameter = parameters.get(i);
                AbstractType fi = new ProperType(TreeUtils.typeOf(parameter), context);
                AbstractType gi = gs.get(i);
                constraintSet.add(new Typing(fi, gi, Constraint.Kind.TYPE_EQUALITY));
            }
            constraintSet.add(new Typing(tPrime, t, Constraint.Kind.SUBTYPE));
        }

        AbstractType R = t.getFunctionTypeReturn();
        if (R != null && R.getTypeKind() != TypeKind.VOID) {
            for (ExpressionTree e : InternalInferenceUtils.getReturnedExpressions(lambda)) {
                if (R.isProper()) {
                    if (!context.env
                            .getTypeUtils()
                            .isAssignable(TreeUtils.typeOf(e), ((ProperType) R).getProperType())) {
                        BoundSet boundSet = new BoundSet(context);
                        boundSet.addFalse();
                        return boundSet;
                    }
                } else {
                    constraintSet.add(new Expression(e, R));
                }
            }
        }
        return constraintSet;
    }

    private static ConstraintSet reduceConditional(Expression constraint) {
        ConditionalExpressionTree conditional =
                (ConditionalExpressionTree) constraint.getExpression();
        Constraint trueConstraint =
                new Expression(conditional.getTrueExpression(), constraint.getT());
        Constraint falseConstraint =
                new Expression(conditional.getFalseExpression(), constraint.getT());
        return new ConstraintSet(trueConstraint, falseConstraint);
    }

    /**
     * Text from JLS 18.2.1: If the expression is a class instance creation expression or a method
     * invocation expression, the constraint reduces to the bound set B3 which would be used to
     * determine the expression's invocation type when targeting T, as defined in 18.5.2. (For a
     * class instance creation expression, the corresponding "method" used for inference is defined
     * in 15.9.3).
     *
     * <p>This bound set may contain new inference variables, as well as dependencies between these
     * new variables and the inference variables in T.
     */
    private static BoundSet reduceMethodInvocation(Expression constraint, Context context) {
        ExpressionTree expressionTree = constraint.getExpression();
        List<? extends ExpressionTree> args;
        if (expressionTree.getKind() == Kind.NEW_CLASS) {
            NewClassTree newClassTree = (NewClassTree) expressionTree;
            args = newClassTree.getArguments();
        } else {
            MethodInvocationTree methodInvocationTree = (MethodInvocationTree) expressionTree;
            args = methodInvocationTree.getArguments();
        }

        ExecutableType methodType =
                InternalInferenceUtils.getTypeOfMethodAdaptedToUse(expressionTree, context);
        Theta map = Theta.theta(expressionTree, methodType, context);
        BoundSet b2 = context.inference.createB2(expressionTree, methodType, args, map);
        return context.inference.createB3(b2, methodType, expressionTree, constraint.getT(), map);
    }

    private static Constraint reduceParenthesized(Expression constraint) {
        assert constraint.getExpression().getKind() == Kind.PARENTHESIZED;
        return new Expression(TreeUtils.skipParens(constraint.getExpression()), constraint.getT());
    }

    private static Constraint reduceStandalone(Expression constraint, Context context) {
        ProperType s = new ProperType(TreeUtils.typeOf(constraint.getExpression()), context);
        return new Typing(s, constraint.getT(), Constraint.Kind.TYPE_COMPATIBILITY);
    }

    /**
     * JSL 18.2.1: "If T is a proper type, the constraint reduces to true if the expression is
     * compatible in a loose invocation context with T (5.3), and false otherwise."
     */
    private static ReductionResult reduceProperType(Expression constraint) {
        // Assume the constraint reduces to TRUE, if it did not the code wouldn't compile with
        // javac.

        // com.sun.tools.javac.code.Types.isConvertible(com.sun.tools.javac.code.Type, com.sun.tools.javac.code.Type)
        return new ConstraintSet();
    }

    private static AbstractType getGroundTargetType(
            AbstractType t, LambdaExpressionTree lambda, Context context) {
        if (!t.isWildcardParameterizedType()) {
            return t;
        }
        // 15.27.3:
        // If T is a wildcard-parameterized functional interface type and the lambda expression is
        // explicitly typed, then the ground target type is inferred as described in 18.5.3.
        if (InternalInferenceUtils.isExplicitlyType(lambda)) {
            return explicitlyTypeLambdasWithWildcard(t, lambda, context);
        } else {
            // If T is a wildcard-parameterized functional interface type and the lambda expression
            // is implicitly typed, then the ground target type is the non-wildcard parameterization (§9.9) of T.
            // https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.9-200-C
            return nonWildcardParameterization(t, context);
        }
    }

    private static AbstractType nonWildcardParameterization(AbstractType t, Context context) {
        List<AbstractType> As = t.getTypeArguments();
        Iterator<ProperType> Bs = t.getTypeParameterBounds();
        List<AbstractType> Ts = new ArrayList<>();
        for (AbstractType Ai : As) {
            ProperType bi = Bs.next();
            if (Ai.getTypeKind() != TypeKind.WILDCARD) {
                Ts.add(Ai);
            } else if (Ai.isUnboundWildcard()) {
                Ts.add(bi);
            } else if (Ai.isUpperBoundedWildcard()) {
                AbstractType Ui = Ai.getWildcardUpperBound();
                AbstractType glb = InferenceUtils.glb(Ui, bi, context);
                Ts.add(glb);
            } else {
                // Lower bounded wildcard
                Ts.add(Ai.getWildcardLowerBound());
            }
        }
        return t.replaceTypeArgs(Ts);
    }

    /** 18.5.3: Functional Interface Parameterization Inference */
    private static AbstractType explicitlyTypeLambdasWithWildcard(
            AbstractType t, LambdaExpressionTree lambda, Context context) {
        // Where a lambda expression with explicit parameter types P1, ..., Pn targets a functional
        // interface type F<A1, ..., Am> with at least one wildcard type argument, then a parameterization
        // of F may be derived as the ground target type of the lambda expression as follows.
        List<ProperType> ps = new ArrayList<>();
        for (VariableTree paramTree : lambda.getParameters()) {
            ps.add(new ProperType(TreeUtils.typeOf(paramTree), context));
        }

        TypeElement typeEle =
                (TypeElement) ((DeclaredType) InferenceUtils.getJavaType(t)).asElement();
        // Let Q1, ..., Qk be the parameter types of the function type of the type F<α1, ..., αm>,
        // where α1, ..., αm are fresh inference variables.
        List<Variable> alphas = new ArrayList<>();
        Theta map = new Theta();
        for (TypeParameterElement param : typeEle.getTypeParameters()) {
            TypeVariable typeVar = (TypeVariable) param.asType();
            Variable ai = new Variable(typeVar, lambda, context);
            map.put(typeVar, ai);
            alphas.add(ai);
        }

        ExecutableType funcType = TypesUtils.findFunctionType(typeEle.asType(), context.env);
        List<AbstractType> qs = new ArrayList<>();
        for (TypeMirror param : funcType.getParameterTypes()) {
            qs.add(InferenceType.create(param, map, context));
        }

        // A set of constraint formulas is formed with, for all i (1 ≤ i ≤ n), ‹Pi = Qi›.
        ConstraintSet constraintSet = new ConstraintSet();
        for (int i = 0; i < ps.size(); i++) {
            ProperType pi = ps.get(i);
            AbstractType qi = qs.get(i);
            constraintSet.add(new Typing(pi, qi, Constraint.Kind.TYPE_EQUALITY));
        }
        // This constraint formula set is reduced to form the bound set B.
        BoundSet b = constraintSet.reduce(context);
        assert !b.containsFalse()
                : "Bound set contains false during Functional Interface Parameterization Inference";

        // A new parameterization of the functional interface type, F<A'1, ..., A'm>, is constructed as follows, for 1 ≤ i ≤ m:
        List<AbstractType> APrimes = new ArrayList<>();
        Iterator<Variable> alphaIter = alphas.iterator();
        boolean hasWildcard = false;
        for (AbstractType Ai : t.getTypeArguments()) {
            Variable alphaI = alphaIter.next();
            // If B contains an instantiation (§18.1.3) for αi, T, then A'i = T.
            AbstractType AiPrime = alphaI.getInstantiation();
            if (AiPrime == null) {
                AiPrime = Ai;
            }
            APrimes.add(AiPrime);
            if (AiPrime.getTypeKind() == TypeKind.WILDCARD) {
                hasWildcard = true;
            }
        }

        // The inferred parameterization is either F<A'1, ..., A'm>, if all the type arguments
        // are types, or the non-wildcard parameterization (§9.9) of F<A'1, ..., A'm>, if one or more type arguments are still wildcards.

        AbstractType target = t.replaceTypeArgs(APrimes);
        if (hasWildcard) {
            return nonWildcardParameterization(target, context);
        }
        return target;
    }
}
