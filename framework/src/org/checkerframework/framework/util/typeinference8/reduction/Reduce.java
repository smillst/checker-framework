package org.checkerframework.framework.util.typeinference8.reduction;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Exception;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Kind;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Expression;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.CheckedExceptions;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.FalseBoundException;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

public class Reduce {
    public BoundSet reduce(ConstraintSet constraintSet, Context context) {
        BoundSet boundSet = new BoundSet(context);
        while (!constraintSet.isEmpty()) {
            Constraint constraint = constraintSet.pop();
            ReductionResult result = reduce(constraint, context);
            if (result instanceof ReductionResultPair) {
                boundSet.add(((ReductionResultPair) result).second);
                if (boundSet.containsFalse()) {
                    throw new FalseBoundException(constraint);
                }
                constraintSet.add(((ReductionResultPair) result).first);
            } else if (result instanceof Constraint) {
                constraintSet.add((Constraint) result);
            } else if (result instanceof ConstraintSet) {
                constraintSet.add((ConstraintSet) result);
            } else if (result instanceof BoundSet) {
                boundSet.add((BoundSet) result);
                if (boundSet.containsFalse()) {
                    throw new FalseBoundException(constraint);
                }
            } else if (result == null) {
                throw new FalseBoundException(constraint);
            } else if (result == ReductionResult.UNCHECKED_CONVERSION) {
                boundSet.setUncheckedConversion(true);
            } else if (result == ReductionResult.TRUE) {
                // loop
            } else {
                throw new RuntimeException("Not found " + result);
            }
        }
        return boundSet;
    }

    public ReductionResult reduce(Constraint constraint, Context context) {
        switch (constraint.getKind()) {
            case EXPRESSION:
                return ReduceExpression.reduce((Expression) constraint, context);
            case TYPE_COMPATIBILITY:
                return ReduceTyping.reduceCompatible((Typing) constraint, context);
            case SUBTYPE:
                return ReduceTyping.reduceSubtyping((Typing) constraint, context);
            case CONTAINED:
                return ReduceTyping.reduceContained((Typing) constraint);
            case TYPE_EQUALITY:
                return ReduceTyping.reduceEquality((Typing) constraint);
            case LAMBDA_EXCEPTION:
            case METHOD_REF_EXCEPTION:
                return reduceException((Exception) constraint, context);
            default:
                ErrorReporter.errorAbort("Unexpected constraint kind: " + constraint.getKind());
                throw new RuntimeException(""); // dead code
        }
    }

    public ReductionResult reduceException(Exception c, Context context) {
        ConstraintSet constraintSet = new ConstraintSet();
        ExecutableElement ele =
                (ExecutableElement) TreeUtils.findFunction(c.getExpression(), context.env);
        List<Variable> es = new ArrayList<>();
        List<ProperType> properTypes = new ArrayList<>();
        for (TypeMirror thrownType : ele.getThrownTypes()) {
            AbstractType ei = InferenceType.create(thrownType, c.getMap(), context);
            if (ei.isProper()) {
                properTypes.add((ProperType) ei);
            } else {
                es.add((Variable) ei);
            }
        }
        if (es.isEmpty()) {
            return ReductionResult.TRUE;
        }

        List<? extends TypeMirror> thrownTypes;
        if (c.getKind() == Kind.LAMBDA_EXCEPTION) {
            thrownTypes = CheckedExceptions.thrownCheckedExceptions(c.getExpression(), context);
        } else {
            thrownTypes =
                    TypesUtils.findFunctionType(TreeUtils.typeOf(c.getExpression()), context.env)
                            .getThrownTypes();
        }

        for (TypeMirror xi : thrownTypes) {
            for (ProperType properType : properTypes) {
                if (context.env.getTypeUtils().isSubtype(xi, properType.getJavaType())) {
                    continue;
                }
            }
            for (Variable ei : es) {
                constraintSet.add(new Typing(new ProperType(xi, context), ei, Kind.SUBTYPE));
                ei.setHasThrowsBound(true);
            }
        }

        return constraintSet;
    }
}
