package org.checkerframework.framework.util.typeinference8.bound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.PluginUtil;
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
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.types.Variable.CaptureVariable;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.javacutil.ErrorReporter;

public class BoundSet implements ReductionResult {
    /**
     * Max number of incorporation loops. Use same constant as {@link
     * com.sun.tools.javac.comp.Infer#MAX_INCORPORATION_STEPS}
     */
    //    private static final int MAX_INCORPORATION_STEPS = 100;
    private static final int MAX_INCORPORATION_STEPS = 100;

    private final Map<Variable, BoundsForVar> boundsOnVariables;
    private final LinkedHashSet<Capture> captures;
    private final LinkedHashSet<Throws> throwsList;

    private final Context context;

    private boolean isFalse = false;

    private BoundSet(Bound false1, Context context) {
        this(context);
        add(false1);
    }

    public BoundSet(Context context) {
        assert context != null;
        this.boundsOnVariables = new LinkedHashMap<>();
        this.captures = new LinkedHashSet<>();
        this.throwsList = new LinkedHashSet<>();
        this.context = context;
        this.isFalse = false;
    }

    public BoundSet(BoundSet toCopy) {
        this(toCopy.context);
        this.isFalse = toCopy.isFalse;
        this.captures.addAll(toCopy.captures);
        this.throwsList.addAll(toCopy.throwsList);
        for (Entry<Variable, BoundsForVar> entry : toCopy.boundsOnVariables.entrySet()) {
            BoundsForVar copy = new BoundsForVar(entry.getValue());
            boundsOnVariables.put(entry.getKey(), copy);
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

        for (Entry<TypeVariable, Variable> entry : map.getEntryList()) {
            TypeVariable pl = entry.getKey();
            Variable al = entry.getValue();
            TypeMirror upperBound = pl.getUpperBound();
            boundSet.add(initialBoundForL(map, al, upperBound, context));
        }
        return boundSet;
    }

    /**
     * If Pl has no TypeBound, the bound {@literal al <: Object} appears in the set. Otherwise, for
     * each type T delimited by & in the TypeBound, the bound {@literal al <: T[P1:=a1,..., Pp:=ap]}
     * appears in the set; if this results in no proper upper bounds for al (only dependencies),
     * then the bound {@literal al <: Object} also appears in the set.
     */
    private static BoundSet initialBoundForL(
            Theta map, Variable al, TypeMirror upperBound, Context context) {
        BoundSet boundSet = new BoundSet(context);
        switch (upperBound.getKind()) {
            case INTERSECTION:
                for (TypeMirror bound : ((IntersectionType) upperBound).getBounds()) {
                    boundSet.add(initialBoundForL(map, al, bound, context));
                }
                break;
            default:
                AbstractType t1 = InferenceType.create(upperBound, map, context);
                boundSet.add(Subtype.createSubtype(al, t1));
                break;
        }
        return boundSet;
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
            case TRUE:
                // Do nothing
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
        add(initialBounds(capture.getMap(), context));

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
            getBoundsForVar(var).addUpperBound(bound.getSupertype());
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
            bound = new BoundsForVar(var, context);
            boundsOnVariables.put(var, bound);
        }
        return bound;
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

    public boolean hasInstantiation(Variable alpha) {
        return getBoundsForVar(alpha).hasInstantiation();
    }

    /** Gets the instantiations for all alphas that currently have one. */
    public List<Instantiation> getInstantiations(List<Variable> alphas) {
        List<Instantiation> list = new ArrayList<>();
        for (Variable var : alphas) {
            if (boundsOnVariables.containsKey(var)) {
                BoundsForVar bounds = boundsOnVariables.get(var);
                if (bounds.hasInstantiation()) {
                    ProperType properType = bounds.getInstantiation();
                    list.add(new Instantiation(var, properType));
                }
            }
        }
        return list;
    }

    public List<Instantiation> getInstantiationsAll() {
        List<Instantiation> list = new ArrayList<>();
        for (Entry<Variable, BoundsForVar> entry : boundsOnVariables.entrySet()) {
            Variable var = entry.getKey();
            BoundsForVar bounds = entry.getValue();
            if (bounds.hasInstantiation()) {
                ProperType properType = bounds.getInstantiation();
                list.add(new Instantiation(var, properType));
            }
        }
        return list;
    }

    public List<Variable> getInstantiatedVariables() {
        List<Variable> list = new ArrayList<>();
        for (Entry<Variable, BoundsForVar> entry : boundsOnVariables.entrySet()) {
            Variable var = entry.getKey();
            BoundsForVar bounds = entry.getValue();
            if (bounds.hasInstantiation()) {
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
            LinkedHashSet<Variable> lhsVars = capture.getAllIVOnLHS();
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

            BoundsForVar boundsForAlpha = getBoundsForVar(alpha);
            alphaDependencies.addAll(boundsForAlpha.getAllMentionedVars());

            if (alpha instanceof CaptureVariable) {
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
        return Collections.emptyList();
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
    public void incorporateToFixedPoint(BoundSet newBounds) {
        this.isFalse &= newBounds.isFalse;
        if (this.containsFalse()) {
            return;
        }
        int count = 0;
        do {
            count++;
            if (!add(newBounds)) {
                // No new bounds, a fixed point has been reached.
                break;
            }
            List<Instantiation> instantiations = getInstantiationsAll();

            ConstraintSet constraints = new ConstraintSet();
            for (BoundsForVar boundsForVar : boundsOnVariables.values()) {
                if (boundsForVar.var instanceof CaptureVariable) {
                    // call to clear them out.
                    boundsForVar.getConstraintFromComplementaryBounds();
                } else {
                    // See note com.sun.tools.javac.code.Type.CapturedUndetVar
                    constraints.add(boundsForVar.getConstraintFromComplementaryBounds());
                }
                if (!instantiations.isEmpty()) {
                    constraints.add(boundsForVar.getConstraintsFromInst(instantiations));
                }
            }

            for (Capture capture : captures) {
                constraints.add(incorporate(capture));
            }

            newBounds = constraints.reduce(context);
            isFalse &= newBounds.isFalse;
            assert count < MAX_INCORPORATION_STEPS : "Max incorporation steps reached.";
        } while (!isFalse && count < MAX_INCORPORATION_STEPS);
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.3.2 */
    private ConstraintSet incorporate(Capture bound) {
        // Let R be a type that is not an inference variable (but is not necessarily a proper type).
        ConstraintSet constraintSet = new ConstraintSet();
        for (CaptureTuple c : bound.getTuples()) {
            BoundsForVar boundsForAlphaI = getBoundsForVar(c.alpha);
            if (c.capturedTypeArg.getTypeKind() == TypeKind.WILDCARD) {
                ConstraintSet newCon =
                        boundsForAlphaI.getWildcardConstraints(c.capturedTypeArg, c.bound);
                if (newCon == null) {
                    this.isFalse = true;
                    return new ConstraintSet();
                }
                constraintSet.add(newCon);
            }
        }
        return constraintSet;
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
        } else if (boundsOnVariables.isEmpty()) {
            return "EMPTY";
        }
        String vars = PluginUtil.join(", ", getInstantiatedVariables());
        if (vars.isEmpty()) {
            return "No instantiated variables";
        } else {
            return "Instantiated variables: " + vars;
        }
    }

    /**
     * @return whether or not one of the primitive wrapper classes mentioned is an instantiation,
     *     upper bound, or lower
     */
    public boolean hasPrimitiveWrapperBound(Variable alpha) {
        BoundsForVar boundsForVar = getBoundsForVar(alpha);
        return boundsForVar.hasPrimitiveWrapperBound();
    }

    /**
     * @return true if there exists a bound of one of the forms {@code alpha = S} or {@code S <:
     *     alpha}, where S is a wildcard-parameterized type.
     */
    public boolean hasWildcardParameterizedLowerOrEqualBound(Variable alpha) {
        BoundsForVar boundsForVar = getBoundsForVar(alpha);
        return boundsForVar.hasWildcardParameterizedLowerOrEqualBound();
    }

    /**
     * Does this bound set contain two bounds of the forms {@code S1 <: alpha} and {@code S2 <:
     * alpha}, where S1 and S2 have supertypes that are two different parameterizations of the same
     * generic class or interface?
     */
    public boolean hasLowerBoundDifferentParam(Variable alpha) {
        return getBoundsForVar(alpha).hasLowerBoundDifferentParam();
    }

    /**
     * Does this bound set contain a bound of one of the forms {@code alpha = S} or {@code S <:
     * alpha}, where there exists no type of the form {@code G<...>} that is a supertype of S, but
     * the raw type {@code |G<...>|} is a supertype of S?
     */
    public boolean hasRawTypeLowerOrEqualBound(Variable alpha, AbstractType g) {
        return getBoundsForVar(alpha).hasRawTypeLowerOrEqualBound(g);
    }

    public boolean needsIncorp() {
        for (BoundsForVar boundsForVar : boundsOnVariables.values()) {
            if (boundsForVar.containsUnincorporated()) {
                return true;
            }
        }
        return false;
    }
}
