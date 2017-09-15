package org.checkerframework.framework.util.typeinference8.bound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeKind;
import org.checkerframework.framework.util.typeinference8.bound.Capture.CaptureTuple;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.bound.Subtype.NonProperLowerBound;
import org.checkerframework.framework.util.typeinference8.bound.Subtype.NonProperUpperBound;
import org.checkerframework.framework.util.typeinference8.bound.Subtype.ProperLowerBound;
import org.checkerframework.framework.util.typeinference8.bound.Subtype.ProperUpperBound;
import org.checkerframework.framework.util.typeinference8.bound.Subtype.SubtypeVV;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.resolution.Resolution;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.Dependencies;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.javacutil.ErrorReporter;

public class BoundSet implements ReductionResult {
    /**
     * Max number of incorporation loops. Use same constant as {@link
     * com.sun.tools.javac.comp.Infer#MAX_INCORPORATION_STEPS}
     */
    private static final int MAX_INCORPORATION_STEPS = 100;

    public static final BoundSet TRUE = new BoundSet();

    public static final BoundSet FALSE = new BoundSet(Bound.FALSE);

    private final Map<Variable, BoundsForVar> boundsOnVariables;
    private final LinkedHashSet<Capture> captures;
    private final LinkedHashSet<Throws> throwsList;

    private boolean isFalse = false;

    private BoundSet(Bound false1) {
        this();
        add(false1);
    }

    public BoundSet() {
        boundsOnVariables = new HashMap<>();
        captures = new LinkedHashSet<>();
        throwsList = new LinkedHashSet<>();
    }

    public boolean add(BoundSet newSet) {
        boolean changed = captures.addAll(newSet.captures);
        changed |= throwsList.addAll(newSet.throwsList);
        for (Variable v : newSet.getAllInferenceVariables()) {
            changed |= getBoundsForVar(v).merge(newSet.getBoundsForVar(v));
        }
        isFalse |= newSet.isFalse;
        return changed;
    }

    public void add(Bound bound) {
        switch (bound.getKind()) {
            case EQUAL:
                addEqual((Equal) bound);
                break;
            case SUBTYPE:
                addSubtype((Subtype) bound);
                break;
            case FALSE:
                isFalse = true;
                break;
            case CAPTURE:
                addCapture((Capture) bound);
                break;
            case THROWS:
                throwsList.add((Throws) bound);
                break;
        }
    }

    private void addCapture(Capture capture) {
        captures.add(capture);

        // When a bound set contains a bound of the form G<alpha1, ..., alphan> = capture(G<A1, ..., An>), new
        // bounds are implied and new constraint formulas may be implied, as follows.

        // Let P1, ..., Pn represent the type parameters of G and let B1, ..., Bn represent the
        // bounds of these type parameters. Let θ represent the substitution [P1:=alpha1, ..., Pn:=alphan].
        // Let R be a type that is not an inference variable (but is not necessarily a proper type).

        // A set of bounds on alpha1, ..., alphan is implied, constructed from the declared bounds of
        // P1, ..., Pn as specified in §18.1.3.
        add(BoundUtil.initialBounds(capture.getMap(), null));

        // If Ai is not a wildcard, then the bound αi = Ai is implied.
        for (Bound b : capture.getInitialBounds()) {
            add(b);
        }
    }

    private void addEqual(Equal bound) {
        BoundsForVar boundsForVar = getBoundsForVar(bound.getA());
        boundsForVar.addEqual(bound.getT());

        if (bound.getT().getKind() == AbstractType.Kind.VARIABLE) {
            Variable v = (Variable) bound.getT();
            getBoundsForVar(v).addEqual(bound.getA());
        }
    }

    private void addSubtype(Subtype bound) {
        if (bound instanceof SubtypeVV) {
            Variable subVar = ((SubtypeVV) bound).getSubtype();
            Variable superVar = ((SubtypeVV) bound).getSupertype();
            getBoundsForVar(subVar).addUpperBound(superVar);
            getBoundsForVar(subVar).addLowerBound(subVar);
        } else if (bound instanceof ProperUpperBound || bound instanceof NonProperUpperBound) {
            Variable var = (Variable) bound.getSubtype();
            getBoundsForVar(var).addUpperBound(bound.getSubtype());
        } else if (bound instanceof ProperLowerBound || bound instanceof NonProperLowerBound) {
            Variable var = (Variable) bound.getSupertype();
            getBoundsForVar(var).addLowerBound(bound.getSubtype());
        } else {
            ErrorReporter.errorAbort("Unexpected type");
            throw new RuntimeException("");
        }
    }

    private BoundsForVar getBoundsForVar(Variable var) {
        BoundsForVar bound = boundsOnVariables.get(var);
        if (bound == null) {
            bound = new BoundsForVar(var);
            boundsOnVariables.put(var, bound);
        }
        return bound;
    }

    /**
     * Does the bound set contain a bound of the form {@code G<..., ai, ...> = capture(G<...>) }?
     */
    public boolean containsCapture(Collection<Variable> as) {
        // TODO: Implement
        throw new RuntimeException("Not Implemented");
    }

    public boolean containsFalse() {
        return isFalse;
    }

    public boolean containsProperUpperBound(Variable a) {
        return getBoundsForVar(a).hasProperUpperBound();
    }

    public LinkedHashSet<ProperType> findProperLowerBounds(Variable a) {
        return getBoundsForVar(a).getProperLowerBounds();
    }

    public LinkedHashSet<ProperType> findProperUpperBounds(Variable a) {
        return getBoundsForVar(a).getProperUpperBounds();
    }

    public ProperType getInstantiation(Variable alpha) {
        return getBoundsForVar(alpha).getInstantiation();
    }

    public List<Instantiation> getInstantiations(List<Variable> alphas) {
        // TODO:
        throw new RuntimeException("Not implemented");
    }

    public List<Instantiation> getInstantiationsAll() {
        // TODO:
        throw new RuntimeException("Not implemented");
    }

    /** Resolve all inference variables mentioned in any bound. */
    public List<Instantiation> resolve(ProcessingEnvironment env, Theta map) {
        BoundSet b = Resolution.resolve(getAllInferenceVariables(), this, env, map);
        return b.getInstantiations(getAllInferenceVariables());
    }

    /** JLS 18.4. Guides order of resolution. */
    public Dependencies getDependencies() {
        Dependencies dependencies = new Dependencies();

        LinkedHashSet<Variable> lhsCapture = new LinkedHashSet<>();
        for (Capture capture : captures) {
            LinkedHashSet<Variable> lhsVars = capture.getAllIVOnLHS();
            LinkedHashSet<Variable> rhsVars = capture.getAllIVOnRHS();
            for (Variable var : lhsVars) {
                // An inference variable alpha appearing on the left-hand side of a bound of the
                // form G<..., alpha, ...> = capture(G<...>) depends on the resolution of every
                // other inference variable mentioned in this bound (on both sides of the = sign).
                dependencies.putOrAddAll(var, rhsVars);
                dependencies.putOrAddAll(var, lhsVars);
            }

            lhsCapture.addAll(lhsVars);
        }

        for (Variable alpha : getAllInferenceVariables()) {
            LinkedHashSet<Variable> alphaDependencies = new LinkedHashSet<>();
            // An inference variable alpha depends on the resolution of itself.
            alphaDependencies.add(alpha);

            BoundsForVar boundsForAlpha = getBoundsForVar(alpha);
            alphaDependencies.addAll(boundsForAlpha.getAllMentionedVars());

            if (lhsCapture.contains(alpha)) {
                // If alpha appears on the left-hand side of another bound of the form
                // G<..., alpha, ...> = capture(G<...>), then beta depends on the resolution of
                // alpha.
                for (Variable beta : alphaDependencies) {
                    dependencies.putOrAdd(beta, alpha);
                }
            } else {
                // Otherwise, alpha depends on the resolution of beta.
                dependencies.putOrAddAll(alpha, alphaDependencies);
            }
        }

        // Add transitive dependencies
        dependencies.addTransitive();

        return dependencies;
    }

    private List<Variable> getAllInferenceVariables() {
        // TODO: sort
        return new ArrayList<>(boundsOnVariables.keySet());
    }

    public List<Throws> findThrowsBounds(Variable ai) {
        // TODO: Implement
        throw new RuntimeException("Not implemented");
    }

    /**
     * Incorporates {@code newBounds} into this bounds set.
     *
     * <p>Incorporation creates new constraints that are then reduce to a bound set which is further
     * incorporated into this bound set. Incorporation terminates when the bounds set has reached a
     * fixed point. <a
     * href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.3">JLS 18 .1</a>
     * defines this fixed point and further explains incorporation.
     *
     * @param newBounds bounds to incorporate
     * @param map type vars to inference vars
     */
    public void incorporateToFixedPoint(BoundSet newBounds, Theta map) {
        if (newBounds.containsFalse() || this.containsFalse()) {
            return;
        }
        boolean changed;
        int count = 0;
        do {
            count++;
            changed = add(newBounds);

            ConstraintSet constraints = new ConstraintSet();
            for (BoundsForVar boundsForVar : boundsOnVariables.values()) {
                constraints.add(boundsForVar.getConstraintFromComplementaryBounds());
            }

            List<Instantiation> instantiations = getInstantiationsAll();
            for (BoundsForVar boundsForVar : boundsOnVariables.values()) {
                constraints.add(boundsForVar.getConstraintsFromInst(instantiations));
            }

            for (Capture capture : captures) {
                constraints.add(incorporate(capture));
            }

            newBounds = constraints.reduce(map);
        } while (!isFalse
                && !newBounds.containsFalse()
                && changed
                && count < MAX_INCORPORATION_STEPS);
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.3.2 */
    private ConstraintSet incorporate(Capture bound) {
        // Let R be a type that is not an inference variable (but is not necessarily a proper type).
        ConstraintSet constraintSet = new ConstraintSet();
        for (CaptureTuple c : bound.getTuples()) {
            BoundsForVar boundsForAlphaI = getBoundsForVar(c.alpha);
            if (c.typeArg.getTypeKind() == TypeKind.WILDCARD) {
                ConstraintSet newCon = boundsForAlphaI.getWildcardConstraints(c.typeArg, c.bound);
                if (newCon == null) {
                    this.isFalse = true;
                    return new ConstraintSet();
                }
                constraintSet.add(newCon);
            }
        }
        return constraintSet;
    }
}
