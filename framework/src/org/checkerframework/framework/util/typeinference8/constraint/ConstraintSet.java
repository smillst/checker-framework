package org.checkerframework.framework.util.typeinference8.constraint;

import com.sun.source.tree.LambdaExpressionTree;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Kind;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.ThrowsConstraint;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.reduction.ReduceExpression;
import org.checkerframework.framework.util.typeinference8.reduction.ReduceTyping;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResultPair;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.Dependencies;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.CheckedExceptionsUtil;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.FalseBoundException;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

public class ConstraintSet implements ReductionResult {
    public static final ConstraintSet TRUE =
            new ConstraintSet() {
                @Override
                public String toString() {
                    return "TRUE";
                }
            };
    /**
     * This needs to be kept in the order created, which should be lexically left to right. This is
     * for {@link #getMagicalSubSet(Dependencies)}.
     */
    private final List<Constraint> list = new ArrayList<>();

    public ConstraintSet(Constraint... constraints) {
        if (constraints != null) {
            list.addAll(Arrays.asList(constraints));
        }
    }

    /** Adds {@code c} to this set, if c isn't already in the list. */
    public void add(Constraint c) {
        if (c != null) {
            if (!list.contains(c)) {
                list.add(c);
            }
        }
    }

    /** Adds all constraints in {@code constraintSet} to this constraint set. */
    public void addAll(ConstraintSet constraintSet) {
        list.addAll(constraintSet.list);
    }

    /** @return whether or not this constraint set is empty. */
    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * Removes and returns the first constraint that was added to this set.
     *
     * @return first constraint that was added to this set
     */
    public Constraint pop() {
        assert !isEmpty();
        return list.remove(0);
    }

    /** Remove all constraints in {@code subset} from this constraint set. */
    public void remove(ConstraintSet subset) {
        if (this == subset) {
            list.clear();
        }
        list.removeAll(subset.list);
    }

    /**
     * A subset of constraints is selected in C, satisfying the property that, for each constraint,
     * no input variable can influence an output variable of another constraint in C.
     */
    public ConstraintSet getMagicalSubSet(Dependencies dependencies) {
        ConstraintSet subset = new ConstraintSet();
        Set<Variable> inputDependencies = new LinkedHashSet<>();
        Set<Variable> outDependencies = new LinkedHashSet<>();
        for (Constraint c : list) {
            if (c.getKind() == Kind.EXPRESSION
                    || c.getKind() == Kind.LAMBDA_EXCEPTION
                    || c.getKind() == Kind.METHOD_REF_EXCEPTION) {
                Set<Variable> newInputs = dependencies.get(c.getInputVariables());
                Set<Variable> newOutputs = dependencies.get(c.getOutputVariables());
                if (Collections.disjoint(newInputs, outDependencies)
                        && Collections.disjoint(newOutputs, inputDependencies)) {
                    inputDependencies.addAll(newInputs);
                    outDependencies.addAll(newOutputs);
                    subset.add(c);
                } else {
                    //there is a cycle (or cycles) in the graph of dependencies between constraints.
                    subset = new ConstraintSet();
                    break;
                }
            } else {
                subset.add(c);
            }
        }

        if (!subset.isEmpty()) {
            return subset;
        }

        outDependencies.clear();
        inputDependencies.clear();
        // If this subset is empty, then there is a cycle (or cycles) in the graph of dependencies
        // between constraints.
        List<Constraint> consideredConstraints = new ArrayList<>();
        for (Constraint c : list) {
            Set<Variable> newInputs = dependencies.get(c.getInputVariables());
            Set<Variable> newOutputs = dependencies.get(c.getOutputVariables());
            if (inputDependencies.isEmpty()
                    || !Collections.disjoint(newInputs, outDependencies)
                    || !Collections.disjoint(newOutputs, inputDependencies)) {
                inputDependencies.addAll(newInputs);
                outDependencies.addAll(newOutputs);
                consideredConstraints.add(c);
            }
        }

        // A single constraint is selected from the considered constraints, as follows:

        // If any of the considered constraints have the form <Expression -> T>, then the selected
        // constraint is the considered constraint of this form that contains the expression to the
        // left (3.5) of the expression of every other considered constraint of this form.

        // If no considered constraint has the form <Expression -> T>, then the selected constraint
        // is the considered constraint that contains the expression to the left of the expression
        // of every other considered constraint.

        for (Constraint c : consideredConstraints) {
            if (c.getKind() == Kind.EXPRESSION) {
                return new ConstraintSet(c);
            }
        }

        return new ConstraintSet(consideredConstraints.get(0));
    }

    /** @return all variables mentioned by any constraint in this set */
    public List<Variable> getAllInferenceVariables() {
        Set<Variable> vars = new HashSet<>();
        for (Constraint constraint : list) {
            vars.addAll(constraint.getInferenceVariables());
        }
        return new ArrayList<>(vars);
    }

    /** @return all input variables for all constraints in this set */
    public List<Variable> getAllInputVariables() {
        Set<Variable> vars = new HashSet<>();
        for (Constraint constraint : list) {
            vars.addAll(constraint.getInputVariables());
        }
        return new ArrayList<>(vars);
    }

    /** Applies the instantiations to all the constraints in this set. */
    public void applyInstantiations(List<Variable> instantiations) {
        for (Constraint constraint : list) {
            constraint.applyInstantiations(instantiations);
        }
    }

    @Override
    public String toString() {
        return "Size: " + list.size();
    }

    /**
     * Reduces all the constraints in this set. (See JLS 18.2)
     *
     * @return the bound set produce by reducing this constraint set
     */
    public BoundSet reduce(Context context) {
        BoundSet boundSet = new BoundSet(context);
        while (!this.isEmpty()) {
            Constraint constraint = this.pop();
            ReductionResult result = reduce(constraint, context);
            if (result instanceof ReductionResultPair) {
                boundSet.merge(((ReductionResultPair) result).second);
                if (boundSet.containsFalse()) {
                    throw new FalseBoundException(constraint);
                }
                this.addAll(((ReductionResultPair) result).first);
            } else if (result instanceof Constraint) {
                this.add((Constraint) result);
            } else if (result instanceof ConstraintSet) {
                this.addAll((ConstraintSet) result);
            } else if (result instanceof BoundSet) {
                boundSet.merge((BoundSet) result);
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

    /**
     * Reduces a single constraint.
     *
     * @param constraint
     * @param context
     * @return the result of reduction: a bound set, a constraint set, or a single constraint
     */
    private ReductionResult reduce(Constraint constraint, Context context) {
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
                return reduceException((ThrowsConstraint) constraint, context);
            default:
                ErrorReporter.errorAbort("Unexpected constraint kind: " + constraint.getKind());
                throw new RuntimeException(""); // dead code
        }
    }

    private ReductionResult reduceException(ThrowsConstraint c, Context context) {
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
            thrownTypes =
                    CheckedExceptionsUtil.thrownCheckedExceptions(
                            (LambdaExpressionTree) c.getExpression(), context);
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
