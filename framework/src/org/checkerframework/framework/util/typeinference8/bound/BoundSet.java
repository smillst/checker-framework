package org.checkerframework.framework.util.typeinference8.bound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.framework.util.PluginUtil;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.reduction.ReduceTyping;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.resolution.Resolution;
import org.checkerframework.framework.util.typeinference8.types.Dependencies;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.types.Variable.CaptureVariable;
import org.checkerframework.framework.util.typeinference8.util.Context;

public class BoundSet implements ReductionResult {
    /**
     * Max number of incorporation loops. Use same constant as {@link
     * com.sun.tools.javac.comp.Infer#MAX_INCORPORATION_STEPS}
     */
    private static final int MAX_INCORPORATION_STEPS = 100;

    private final LinkedHashSet<Variable> variables;
    private final LinkedHashSet<Capture> captures;

    private final Context context;

    private boolean isFalse = false;
    private boolean uncheckedConversion = false;

    private BoundSet(Bound false1, Context context) {
        this(context);
        isFalse = true;
    }

    public BoundSet(Context context) {
        assert context != null;
        this.variables = new LinkedHashSet<>();
        this.captures = new LinkedHashSet<>();
        this.context = context;
        this.isFalse = false;
    }

    public BoundSet(BoundSet toCopy) {
        this(toCopy.context);
        this.isFalse = toCopy.isFalse;
        this.captures.addAll(toCopy.captures);
        this.variables.addAll(toCopy.variables);
        this.uncheckedConversion = toCopy.uncheckedConversion;
        for (Variable v : variables) {
            v.save();
        }
    }

    public void restore() {
        for (Variable v : variables) {
            v.restore();
        }
    }

    /**
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.1.3-410:
     *
     * <p>When inference begins, a bound set is typically generated from a list of type parameter
     * declarations P1, ..., Pp and associated inference variables a1, ..., ap.
     *
     * <p>Such a bound set is constructed as follows:
     *
     * <p>For each l ({@literal 1 <= l <= p)}:
     *
     * <p>If Pl has no TypeBound, the bound {@literal al <: Object} appears in the set.
     *
     * <p>Otherwise, for each type T delimited by & in the TypeBound, the bound {@literal al <:
     * T[P1:=a1,..., Pp:=ap]} appears in the set; if this results in no proper upper bounds for al
     * (only dependencies), then the bound {@literal al <: Object} also appears in the set.
     */
    public static BoundSet initialBounds(Theta map, Context context) {
        BoundSet boundSet = new BoundSet(context);
        boundSet.variables.addAll(map.values());
        return boundSet;
    }

    public boolean add(BoundSet newSet) {
        boolean changed = captures.addAll(newSet.captures);
        changed |= variables.addAll(newSet.variables);
        isFalse |= newSet.isFalse;
        uncheckedConversion |= newSet.uncheckedConversion;
        return changed;
    }

    public void addFalse() {
        isFalse = true;
    }

    public boolean isUncheckedConversion() {
        return uncheckedConversion;
    }

    public void setUncheckedConversion(boolean uncheckedConversion) {
        this.uncheckedConversion = uncheckedConversion;
    }

    public void addCapture(Capture capture) {
        captures.add(capture);
        variables.addAll(capture.getAllIVOnLHS());
    }

    /**
     * Does the bound set contain a bound of the form {@code G<..., ai, ...> = capture(G<...>) }?
     */
    public boolean containsCapture(Collection<Variable> as) {
        List<Variable> list = new ArrayList<>();
        for (Capture c : captures) {
            list.addAll(c.getAllIVOnLHS());
        }
        for (Variable ai : as) {
            if (list.contains(ai)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsFalse() {
        return isFalse;
    }

    /** Gets the instantiations for all alphas that currently have one. */
    public List<Instantiation> getInstantiations(List<Variable> alphas) {
        List<Instantiation> list = new ArrayList<>();
        for (Variable var : alphas) {
            if (var.hasInstantiation()) {
                list.add(new Instantiation(var, var.getInstantiation()));
            }
        }
        return list;
    }

    public List<Instantiation> getInstantiationsAll() {
        List<Instantiation> list = new ArrayList<>();
        for (Variable var : variables) {
            if (var.hasInstantiation()) {
                list.add(new Instantiation(var, var.getInstantiation()));
            }
        }
        return list;
    }

    public List<Variable> getInstantiatedVariables() {
        List<Variable> list = new ArrayList<>();
        for (Variable var : variables) {
            if (var.hasInstantiation()) {
                list.add(var);
            }
        }
        return list;
    }

    /** Resolve all inference variables mentioned in any bound. */
    public List<Instantiation> resolve() {
        BoundSet b = Resolution.resolve(getAllInferenceVariables(), this, context);
        return b.getInstantiations(getAllInferenceVariables());
    }

    /** JLS 18.4. Guides order of resolution. */
    public Dependencies getDependencies() {
        return getDependencies(null);
    }

    public Dependencies getDependencies(ConstraintSet c) {
        Dependencies dependencies = new Dependencies();

        for (Capture capture : captures) {
            List<? extends CaptureVariable> lhsVars = capture.getAllIVOnLHS();
            LinkedHashSet<Variable> rhsVars = capture.getAllIVOnRHS();
            for (Variable var : lhsVars) {
                // An inference variable alpha appearing on the left-hand side of a bound of the
                // form G<..., alpha, ...> = capture(G<...>) depends on the resolution of every
                // other inference variable mentioned in this bound (on both sides of the = sign).
                dependencies.putOrAddAll(var, rhsVars);
                dependencies.putOrAddAll(var, lhsVars);
            }
        }
        Set<Variable> set = new LinkedHashSet<>(getAllInferenceVariables());
        if (c != null) {
            set.addAll(c.getAllInferenceVariables());
        }
        for (Variable alpha : set) {
            LinkedHashSet<Variable> alphaDependencies = new LinkedHashSet<>();
            // An inference variable alpha depends on the resolution of itself.
            alphaDependencies.add(alpha);
            alphaDependencies.addAll(alpha.getAllMentionedVars());

            if (alpha.isCaptureVariable()) {
                // If alpha appears on the left-hand side of another bound of the form
                // G<..., alpha, ...> = capture(G<...>), then beta depends on the resolution of
                // alpha.
                for (Variable beta : alphaDependencies) {
                    dependencies.putOrAdd(beta, alpha);
                }
            } else {
                for (Variable beta : alphaDependencies) {
                    if (!beta.isCaptureVariable()) {
                        // Otherwise, alpha depends on the resolution of beta.
                        dependencies.putOrAdd(alpha, beta);
                    }
                }
            }
        }

        // Add transitive dependencies
        dependencies.addTransitive();

        return dependencies;
    }

    private List<Variable> getAllInferenceVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Incorporates {@code newBounds} into this bounds set.
     *
     * <p>Incorporation creates new constraints that are then reduced to a bound set which is
     * further incorporated into this bound set. Incorporation terminates when the bounds set has
     * reached a fixed point. <a
     * href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.3">JLS 18 .1</a>
     * defines this fixed point and further explains incorporation.
     *
     * @param newBounds bounds to incorporate
     */
    public void incorporateToFixedPoint(final BoundSet newBounds) {
        this.isFalse |= newBounds.isFalse;
        if (this.containsFalse()) {
            return;
        }
        add(newBounds);
        int count = 0;
        do {
            count++;
            List<Instantiation> instantiations = getInstantiationsAll();
            boolean boundsChangeInst = false;
            if (!instantiations.isEmpty()) {
                for (Variable var : variables) {
                    boundsChangeInst = var.applyInstantiationsToBounds(instantiations);
                }
            }
            boundsChangeInst |= captures.addAll(newBounds.captures);
            for (Variable alpha : variables) {
                while (!alpha.constraints.isEmpty()) {
                    boundsChangeInst = true;
                    if (!ReduceTyping.reduceTyping(this, alpha.constraints.remove(), context)) {
                        this.isFalse = true;
                        return;
                    }
                }
            }
            if (newBounds.isUncheckedConversion()) {
                this.setUncheckedConversion(true);
            }

            if (!boundsChangeInst) {
                return;
            }

            isFalse &= newBounds.isFalse;
            assert count < MAX_INCORPORATION_STEPS : "Max incorporation steps reached.";
        } while (!isFalse && count < MAX_INCORPORATION_STEPS);
    }

    public void removeCaptures(LinkedHashSet<Variable> as) {
        captures.removeIf((Capture c) -> c.isCaptureMentionsAny(as));
    }

    public void removeCapture(Variable a) {
        captures.removeIf((Capture c) -> c.isCaptureMentionsAny(Collections.singleton(a)));
    }

    @Override
    public String toString() {
        if (isFalse) {
            return "FALSE";
        } else if (variables.isEmpty()) {
            return "EMPTY";
        }
        String vars = PluginUtil.join(", ", getInstantiatedVariables());
        if (vars.isEmpty()) {
            return "No instantiated variables";
        } else {
            return "Instantiated variables: " + vars;
        }
    }
}
