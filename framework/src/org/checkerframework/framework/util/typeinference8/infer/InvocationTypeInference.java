package org.checkerframework.framework.util.typeinference8.infer;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.bound.Capture;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Kind;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Expression;
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

public class InvocationTypeInference {
    private final ProcessingEnvironment env;
    private final TreePath pathToExpression;

    private final Context context;

    public InvocationTypeInference(AnnotatedTypeFactory factory, TreePath pathToExpression) {
        this.env = factory.getProcessingEnv();
        this.pathToExpression = pathToExpression;
        this.context = new Context(env, factory, pathToExpression, this);
    }

    public List<Instantiation> infer(MethodInvocationTree methodInvocation) {
        TypeMirror returnType = InferenceUtils.assignedTo(pathToExpression);
        ProperType r = returnType != null ? new ProperType(returnType, context) : null;
        List<Instantiation> result = infer(methodInvocation, r);
        checkResult(result, methodInvocation);
        return result;
    }

    private void checkResult(List<Instantiation> result, MethodInvocationTree methodInvocation) {
        Map<TypeVariable, TypeMirror> fromReturn =
                InferenceUtils.getMappingFromReturnType(
                        methodInvocation, TreeUtils.elementFromUse(methodInvocation), context.env);
        for (Instantiation inst : result) {
            TypeVariable typeVariable = inst.getA().getTypeVariable();
            if (fromReturn.containsKey(typeVariable)) {
                TypeMirror correctType = fromReturn.get(typeVariable);
                TypeMirror inferredType = inst.getT().getProperType();
                if (!context.types.isSameType((Type) correctType, (Type) inferredType, false)) {
                    context.factory
                            .getContext()
                            .getChecker()
                            .report(Result.failure(""), methodInvocation);
                }
            }
        }
    }

    /**
     * @param methodInvocation
     * @param target Nullable if methodInvocation isn't assigned.
     * @return
     */
    public List<Instantiation> infer(MethodInvocationTree methodInvocation, AbstractType target) {
        ExecutableElement element = TreeUtils.elementFromUse(methodInvocation);
        Theta map = Theta.theta(element, methodInvocation, context);
        BoundSet b2 = createB2(element, methodInvocation.getArguments(), map);
        BoundSet b3;
        if (target != null && InternalInferenceUtils.isPolyExpression(methodInvocation)) {
            b3 = createB3(b2, element, methodInvocation, target, map);
        } else {
            b3 = b2;
        }

        ConstraintSet c = createC(element, methodInvocation.getArguments(), map);

        BoundSet b4 = getB4(b3, c);
        List<Instantiation> thetaPrime = b4.resolve();
        // TODO: implement
        boolean uncheckedConversion = false;
        if (uncheckedConversion) {}
        return thetaPrime;
    }

    public BoundSet createB2(
            ExecutableElement element, List<? extends ExpressionTree> args, Theta map) {
        BoundSet b0 = BoundSet.initialBounds(map, context);
        // TODO:
        // For all i (1 ≤ i ≤ p), if Pi appears in the throws clause of m, then the bound throws
        // αi is implied. These bounds, if any, are incorporated with B0 to produce a new bound set, B1.
        BoundSet b1 = b0;
        ConstraintSet c = new ConstraintSet();
        List<AbstractType> formals = getFormals(element, map, args.size());

        for (int i = 0; i < formals.size(); i++) {
            ExpressionTree ei = args.get(i);
            AbstractType fi = formals.get(i);

            if (!InternalInferenceUtils.notPertinentToApplicability(ei, fi.isVariable())) {
                c.add(new Expression(ei, fi));
            }
        }

        BoundSet newBounds = c.reduce(context);
        b1.incorporateToFixedPoint(newBounds);

        return b1;
    }

    public List<AbstractType> getFormals(ExecutableElement element, Theta map, int size) {
        ExecutableType executableType = (ExecutableType) element.asType();
        List<TypeMirror> params = new ArrayList<>(executableType.getParameterTypes());
        if (element.isVarArgs()) {
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
            c.remove(subset);
            List<Variable> alphas = c.getAllInferenceVariables();
            current = Resolution.resolve(alphas, current, context);
            c.applyInstantiations(current.getInstantiations(alphas), context);
            BoundSet newBounds = c.reduce(context);
            current.incorporateToFixedPoint(newBounds);
        }
        return current;
    }

    public BoundSet createB3(
            BoundSet b2,
            ExecutableElement element,
            ExpressionTree invocation,
            AbstractType target,
            Theta map) {
        AbstractType r = InferenceType.create(element.getReturnType(), map, context);
        // TODO: https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.5.2-100-C.1-A
        // If unchecked conversion was necessary for the method to be applicable during
        // constraint set reduction in §18.5.1, the constraint formula ‹|R| → T› is reduced and
        // incorporated with B2.
        // else
        if (r.isWildcardParameterizedType()) {
            // Otherwise, if R θ is a parameterized type, G<A1, ..., An>, and one of A1, ...,
            // An is a wildcard, then, for fresh inference variables β1, ..., βn, the constraint
            // formula ‹G<β1, ..., βn> → T› is reduced and incorporated, along with the bound
            // G<β1, ..., βn> = capture(G<A1, ..., An>), with B2.
            Capture capture = new Capture(r, invocation, context);
            ConstraintSet set =
                    new ConstraintSet(
                            new Typing(capture.getLHS(), target, Kind.TYPE_COMPATIBILITY));
            BoundSet newBounds = set.reduce(context);
            newBounds.add(capture);
            b2.incorporateToFixedPoint(newBounds);
            return b2;
        } else if (r.isVariable()) {
            Variable alpha = (Variable) r;
            BoundSet resolve = Resolution.resolve(Collections.singletonList(alpha), b2, context);
            ProperType u = resolve.getInstantiation(alpha);
            ConstraintSet constraintSet =
                    new ConstraintSet(new Typing(u, target, Kind.TYPE_COMPATIBILITY));
            BoundSet newBounds = constraintSet.reduce(context);
            resolve.incorporateToFixedPoint(newBounds);
            return resolve;
        } else {
            ConstraintSet constraintSet =
                    new ConstraintSet(new Typing(r, target, Kind.TYPE_COMPATIBILITY));
            BoundSet newBounds = constraintSet.reduce(context);
            b2.incorporateToFixedPoint(newBounds);
            return b2;
        }
    }

    private ConstraintSet createC(
            ExecutableElement element, List<? extends ExpressionTree> args, Theta map) {
        ConstraintSet c = new ConstraintSet();
        List<AbstractType> formals = getFormals(element, map, args.size());

        for (int i = 0; i < formals.size(); i++) {
            ExpressionTree ei = args.get(i);
            AbstractType fi = formals.get(i);
            c.add(getConstraint(ei, fi, map));
        }

        return c;
    }

    private ConstraintSet getConstraint(ExpressionTree ei, AbstractType fi, Theta map) {
        ConstraintSet c = new ConstraintSet();
        if (InternalInferenceUtils.notPertinentToApplicability(ei, fi.isVariable())) {
            c.add(new Expression(ei, fi));
        }
        switch (ei.getKind()) {
            case LAMBDA_EXPRESSION:
                LambdaExpressionTree lambda = (LambdaExpressionTree) ei;
                c.add(new Expression(lambda, fi));
                for (ExpressionTree expression :
                        InternalInferenceUtils.getReturnedExpressions(lambda)) {
                    c.add(getConstraint(expression, fi, map));
                }
                break;
            case MEMBER_REFERENCE:
                c.add(new Expression(ei, fi));
                break;
            case METHOD_INVOCATION:
                if (InternalInferenceUtils.isPolyExpression(ei)) {
                    MethodInvocationTree methodInvocation = (MethodInvocationTree) ei;
                    ExecutableElement ele = TreeUtils.elementFromUse(methodInvocation);
                    Theta newMap = Theta.theta(ele, methodInvocation, context);
                    c.add(createC(ele, methodInvocation.getArguments(), newMap));
                }
                break;
            case NEW_CLASS:
                if (InternalInferenceUtils.isPolyExpression(ei)) {
                    NewClassTree newClassTree = (NewClassTree) ei;
                    ExecutableElement ele = TreeUtils.elementFromUse(newClassTree);
                    Theta newMap = Theta.theta(ele, newClassTree, context);
                    c.add(createC(ele, newClassTree.getArguments(), newMap));
                }
                break;
            case PARENTHESIZED:
                c.add(getConstraint(TreeUtils.skipParens(ei), fi, map));
                break;
            case CONDITIONAL_EXPRESSION:
                ConditionalExpressionTree conditional = (ConditionalExpressionTree) ei;
                c.add(getConstraint(conditional.getTrueExpression(), fi, map));
                c.add(getConstraint(conditional.getFalseExpression(), fi, map));
                break;
            default:
                // no constraints
        }

        return c;
    }
}
