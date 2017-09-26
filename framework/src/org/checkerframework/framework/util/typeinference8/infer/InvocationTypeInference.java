package org.checkerframework.framework.util.typeinference8.infer;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.bound.Capture;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Kind;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.LambdaExpression;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.MemberReferenceExpression;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Expression;
import org.checkerframework.framework.util.typeinference8.resolution.Resolution;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceTypeUtil;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InferenceUtils;
import org.checkerframework.framework.util.typeinference8.util.InternalUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

public class InvocationTypeInference {
    private final ProcessingEnvironment env;
    private final TypeMirror object;
    private final AnnotatedTypeFactory factory;
    private final TreePath pathToExpression;

    private final Context context;

    public InvocationTypeInference(AnnotatedTypeFactory factory, TreePath pathToExpression) {
        this.env = factory.getProcessingEnv();
        this.object =
                TypesUtils.typeFromClass(
                        factory.getContext().getTypeUtils(),
                        factory.getElementUtils(),
                        Object.class);
        this.factory = factory;
        this.pathToExpression = pathToExpression;
        this.context = new Context(env, object, factory, pathToExpression, this);
    }

    public List<Instantiation> infer(MethodInvocationTree methodInvocation) {
        TypeMirror returnType = InferenceUtils.assignedTo(pathToExpression);
        ProperType r = returnType != null ? new ProperType(returnType) : null;
        return infer(methodInvocation, r);
    }

    /**
     * @param methodInvocation
     * @param target Nullable
     * @return
     */
    public List<Instantiation> infer(MethodInvocationTree methodInvocation, AbstractType target) {
        ExecutableElement element = TreeUtils.elementFromUse(methodInvocation);
        Theta map = Theta.theta(element);
        BoundSet b2 = createB2(methodInvocation, map);
        BoundSet b3;
        if (target != null && InternalUtils.isPolyExpression(methodInvocation)) {
            b3 = createB3(b2, methodInvocation, target, map);
        } else {
            b3 = b2;
        }

        ConstraintSet c = createC(element, methodInvocation, map);

        BoundSet b4 = getB4(map, b3, c);
        List<Instantiation> thetaPrime = b4.resolve(env, map);
        // TODO: implement
        boolean uncheckedConversion = false;
        if (uncheckedConversion) {}
        return thetaPrime;
    }

    public BoundSet createB2(MethodInvocationTree methodInvocation, Theta map) {
        BoundSet b0 = BoundSet.initialBounds(map, context);
        // TODO:
        // For all i (1 ≤ i ≤ p), if Pi appears in the throws clause of m, then the bound throws
        // αi is implied. These bounds, if any, are incorporated with B0 to produce a new bound set, B1.
        BoundSet b1 = b0;
        ExecutableElement element = TreeUtils.elementFromUse(methodInvocation);
        ConstraintSet c = new ConstraintSet();
        List<? extends ExpressionTree> args = methodInvocation.getArguments();
        List<AbstractType> formals = getFormals(element, map, args.size());

        for (int i = 0; i < formals.size(); i++) {
            ExpressionTree ei = args.get(i);
            AbstractType fi = formals.get(i);

            if (!InternalUtils.notPertinentToApplicability(ei, fi.isVariable())) {
                c.add(new Expression(ei, fi));
            }
        }

        BoundSet newBounds = c.reduce(map, context);
        b1.incorporateToFixedPoint(newBounds, map);

        return b1;
    }

    public List<AbstractType> getFormals(ExecutableElement element, Theta map, int size) {
        ExecutableType executableType = (ExecutableType) element.asType();
        List<TypeMirror> params = new ArrayList<>(executableType.getParameterTypes());
        if (element.isVarArgs()) {
            ArrayType vararg = (ArrayType) params.remove(params.size() - 1);
            for (int i = 0; i < size; i++) {
                params.add(vararg.getComponentType());
            }
        }
        return InferenceTypeUtil.create(params, map);
    }

    private BoundSet getB4(Theta map, BoundSet current, ConstraintSet c) {
        while (!c.isEmpty()) {
            ConstraintSet subset = c.getMagicalSubSet(current.getDependencies());
            c.remove(subset);
            List<Variable> alphas = c.getAllInferenceVariables();
            current = Resolution.resolve(alphas, current, map, context);
            c.applyInstantiations(current.getInstantiations(alphas), context);
            BoundSet newBounds = c.reduce(map, context);
            current.incorporateToFixedPoint(newBounds, map);
        }
        return current;
    }

    public BoundSet createB3(
            BoundSet b2, MethodInvocationTree invocation, AbstractType target, Theta map) {
        AbstractType r =
                InferenceTypeUtil.create(TreeUtils.elementFromUse(invocation).getReturnType(), map);
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
            Capture capture = new Capture(r);
            map.putAll(capture.getMap());
            ConstraintSet set =
                    new ConstraintSet(
                            new Typing(capture.getLHS(), target, Kind.TYPE_COMPATIBILITY));
            BoundSet newBounds = set.reduce(map, context);
            newBounds.add(capture);
            b2.incorporateToFixedPoint(newBounds, map);
            return b2;
        } else if (r.isVariable()) {
            Variable alpha = (Variable) r;
            BoundSet resolve =
                    Resolution.resolve(Collections.singletonList(alpha), b2, map, context);
            ProperType u = resolve.getInstantiation(alpha);
            ConstraintSet constraintSet =
                    new ConstraintSet(new Typing(u, target, Kind.TYPE_COMPATIBILITY));
            BoundSet newBounds = constraintSet.reduce(map, context);
            resolve.incorporateToFixedPoint(newBounds, map);
            return resolve;
        } else {
            ConstraintSet constraintSet =
                    new ConstraintSet(new Typing(r, target, Kind.TYPE_COMPATIBILITY));
            BoundSet newBounds = constraintSet.reduce(map, context);
            b2.incorporateToFixedPoint(newBounds, map);
            return b2;
        }
    }

    private ConstraintSet createC(
            ExecutableElement element, MethodInvocationTree methodInvocation, Theta map) {
        ConstraintSet c = new ConstraintSet();
        List<? extends ExpressionTree> args = methodInvocation.getArguments();
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
        if (InternalUtils.notPertinentToApplicability(ei, fi.isVariable())) {
            c.add(new Expression(ei, fi));
        }
        switch (ei.getKind()) {
            case LAMBDA_EXPRESSION:
                LambdaExpressionTree lambda = (LambdaExpressionTree) ei;
                c.add(new LambdaExpression(lambda, fi));
                for (ExpressionTree expression : InternalUtils.getReturnedExpressions(lambda)) {
                    c.add(getConstraint(expression, fi, map));
                }
                break;
            case MEMBER_REFERENCE:
                c.add(new MemberReferenceExpression((MemberReferenceTree) ei, fi));
                break;
            case METHOD_INVOCATION:
                if (InternalUtils.isPolyExpression(ei)) {
                    MethodInvocationTree methodInvocation = (MethodInvocationTree) ei;
                    ExecutableElement ele = TreeUtils.elementFromUse(methodInvocation);
                    map.putAll(Theta.theta(ele));
                    c.add(createC(ele, methodInvocation, map));
                }
                break;
            case NEW_CLASS:
                // TODO: I think this is the same as a method invocation...
                throw new RuntimeException("Not implemented");
                // break;
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
