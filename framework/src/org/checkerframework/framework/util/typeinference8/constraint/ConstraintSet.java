package org.checkerframework.framework.util.typeinference8.constraint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Kind;
import org.checkerframework.framework.util.typeinference8.reduction.Reduce;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.types.Dependencies;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.Context;

public class ConstraintSet implements ReductionResult {
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

    public void add(Constraint c) {
        if (c != null) {
            if (!list.contains(c)) {
                list.add(c);
            }
        }
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public Constraint pop() {
        assert !isEmpty();
        Constraint next = list.iterator().next();
        list.remove(next);
        return next;
    }

    public void add(ConstraintSet result) {
        list.addAll(result.list);
    }

    public BoundSet reduce(Context context) {
        return new Reduce().reduce(this, context);
    }

    /**
     * A subset of constraints is selected in C, satisfying the property that, for each constraint,
     * no input variable can influence an output variable of another constraint in C.
     *
     * @return
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

        // If any of the considered constraints have the form ‹Expression → T›, then the selected
        // constraint is the considered constraint of this form that contains the expression to the
        // left (§3.5) of the expression of every other considered constraint of this form.

        // If no considered constraint has the form ‹Expression → T›, then the selected constraint
        // is the considered constraint that contains the expression to the left of the expression
        // of every other considered constraint.

        for (Constraint c : consideredConstraints) {
            if (c.getKind() == Kind.EXPRESSION) {
                return new ConstraintSet(c);
            }
        }

        return new ConstraintSet(consideredConstraints.get(0));
    }

    public void remove(ConstraintSet subset) {
        if (this == subset) {
            list.clear();
        }
        list.removeAll(subset.list);
    }

    public List<Variable> getAllInferenceVariables() {
        Set<Variable> vars = new HashSet<>();
        for (Constraint constraint : list) {
            vars.addAll(constraint.getInferenceVariables());
        }
        return new ArrayList<>(vars);
    }

    public void applyInstantiations(List<Instantiation> instantiations, Context context) {
        for (Constraint constraint : list) {
            constraint.applyInstantiations(instantiations);
        }
    }
}
