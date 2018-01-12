package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.constraint.Typing;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TypesUtils;

/** An inference variable */
public class Variable extends AbstractType {

    /** Identification number. Used only to make debugging easier. */
    protected final int id;

    /** Type variable for which the instantiation of this variable is a type argument, */
    protected final TypeVariable typeVariable;

    /**
     * The expression for which this variable is being solved. Used to differentiate inference
     * variables for two different invocations to the same method.
     */
    protected final ExpressionTree invocation;

    protected ProperType instantiation = null;

    /**
     * Bounds on this variable. Stored as a map from kind of bound (upper, lower, equal) to a set of
     * {@link AbstractType}s.
     */
    protected final EnumMap<BoundKind, Set<AbstractType>> bounds = new EnumMap<>(BoundKind.class);

    /** Constraints implied by complementary pairs of bounds found during incorporation. */
    public ConstraintSet constraints = new ConstraintSet();

    /** Whether or not this variable has a throws bounds. */
    private boolean hasThrowsBound = false;

    public Variable(TypeVariable typeVariable, ExpressionTree invocation, Context context) {
        this(typeVariable, invocation, context, context.getNextVariableId());
    }

    private Variable(
            TypeVariable typeVariable, ExpressionTree invocation, Context context, int id) {
        super(context);
        assert typeVariable != null;
        this.typeVariable = typeVariable;
        this.invocation = invocation;
        this.id = id;
        bounds.put(BoundKind.EQUAL, new LinkedHashSet<>());
        bounds.put(BoundKind.UPPER, new LinkedHashSet<>());
        bounds.put(BoundKind.LOWER, new LinkedHashSet<>());
    }

    @Override
    public AbstractType create(TypeMirror type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AbstractType capture() {
        return this;
    }

    @Override
    public AbstractType getErased() {
        return this;
    }

    protected EnumMap<BoundKind, LinkedHashSet<AbstractType>> savedBounds = null;

    public void save() {
        savedBounds = new EnumMap<>(BoundKind.class);
        savedBounds.put(BoundKind.EQUAL, new LinkedHashSet<>(bounds.get(BoundKind.EQUAL)));
        savedBounds.put(BoundKind.UPPER, new LinkedHashSet<>(bounds.get(BoundKind.UPPER)));
        savedBounds.put(BoundKind.LOWER, new LinkedHashSet<>(bounds.get(BoundKind.LOWER)));
    }

    public void restore() {
        assert savedBounds != null;
        instantiation = null;
        bounds.clear();
        bounds.put(BoundKind.EQUAL, new LinkedHashSet<>(savedBounds.get(BoundKind.EQUAL)));
        bounds.put(BoundKind.UPPER, new LinkedHashSet<>(savedBounds.get(BoundKind.UPPER)));
        bounds.put(BoundKind.LOWER, new LinkedHashSet<>(savedBounds.get(BoundKind.LOWER)));
        for (AbstractType t : bounds.get(BoundKind.EQUAL)) {
            if (t.isProper()) {
                instantiation = (ProperType) t;
            }
        }
    }

    public void initialBounds(Theta map) {
        TypeMirror upperBound = typeVariable.getUpperBound();
        // If Pl has no TypeBound, the bound {@literal al <: Object} appears in the set. Otherwise, for
        // each type T delimited by & in the TypeBound, the bound {@literal al <: T[P1:=a1,..., Pp:=ap]}
        // appears in the set; if this results in no proper upper bounds for al (only dependencies),
        // then the bound {@literal al <: Object} also appears in the set.
        switch (upperBound.getKind()) {
            case INTERSECTION:
                for (TypeMirror bound : ((IntersectionType) upperBound).getBounds()) {
                    AbstractType t1 = InferenceType.create(bound, map, context);
                    addBound(BoundKind.UPPER, t1);
                }
                break;
            default:
                AbstractType t1 = InferenceType.create(upperBound, map, context);
                addBound(BoundKind.UPPER, t1);
                break;
        }
    }

    @Override
    public TypeVariable getJavaType() {
        return typeVariable;
    }

    public ExpressionTree getInvocation() {
        return invocation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Variable variable = (Variable) o;
        if (context.factory
                .getContext()
                .getTypeUtils()
                .isSameType(typeVariable, variable.typeVariable)) {
            return invocation == variable.invocation;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = typeVariable.toString().hashCode();
        result = 31 * result + Kind.VARIABLE.hashCode();
        result = 31 * result + invocation.hashCode();
        return result;
    }

    @Override
    public Kind getKind() {
        return Kind.VARIABLE;
    }

    @Override
    public Collection<Variable> getInferenceVariables() {
        return Collections.singleton(this);
    }

    @Override
    public AbstractType applyInstantiations(List<Variable> instantiations) {
        for (Variable inst : instantiations) {
            if (inst.equals(this)) {
                return inst.instantiation;
            }
        }
        return this;
    }

    @Override
    public String toString() {
        if (hasInstantiation()) {
            return "a" + id + " := " + getInstantiation();
        }
        return "a" + id;
    }

    public boolean isCaptureVariable() {
        return this instanceof CaptureVariable;
    }

    // <editor-fold defaultstate="collapsed" desc="Bound opps">
    public enum BoundKind {
        LOWER,
        UPPER,
        EQUAL;
    }

    public boolean hasThrowsBound() {
        return hasThrowsBound;
    }

    public void setHasThrowsBound(boolean hasThrowsBound) {
        this.hasThrowsBound = hasThrowsBound;
    }

    public boolean addBound(BoundKind kind, AbstractType type) {
        if (kind == BoundKind.EQUAL && type.isProper()) {
            instantiation = (ProperType) type;
        }
        if (bounds.get(kind).add(type)) {
            addConstraints(kind, type);
            return true;
        }
        return false;
    }

    private void addConstraints(BoundKind kind, AbstractType s) {
        if (kind == BoundKind.EQUAL) {
            for (AbstractType t : bounds.get(BoundKind.EQUAL)) {
                if (s != t) {
                    constraints.add(new Typing(s, t, Typing.Kind.TYPE_EQUALITY));
                }
            }
        } else if (kind == BoundKind.LOWER) {
            for (AbstractType t : bounds.get(BoundKind.EQUAL)) {
                if (s != t) {
                    constraints.add(new Typing(s, t, Typing.Kind.SUBTYPE));
                }
            }
        } else { // UPPER
            for (AbstractType t : bounds.get(BoundKind.EQUAL)) {
                if (s != t) {
                    constraints.add(new Typing(t, s, Typing.Kind.SUBTYPE));
                }
            }
        }

        if (kind == BoundKind.EQUAL || kind == BoundKind.UPPER) {
            for (AbstractType t : bounds.get(BoundKind.LOWER)) {
                if (s != t) {
                    constraints.add(new Typing(t, s, Typing.Kind.SUBTYPE));
                }
            }
        }

        if (kind == BoundKind.EQUAL || kind == BoundKind.LOWER) {
            for (AbstractType t : bounds.get(BoundKind.UPPER)) {
                if (s != t) {
                    constraints.add(new Typing(s, t, Typing.Kind.SUBTYPE));
                }
            }
        }

        if (kind == BoundKind.UPPER) {
            // When a bound set contains a pair of bounds var <: S and var <: T, and there exists
            // a supertype of S of the form G<S1, ..., Sn> and
            // a supertype of T of the form G<T1,..., Tn> (for some generic class or interface, G),
            // then for all i (1 <= i <= n), if Si and Ti are types (not wildcards),
            // the constraint formula <Si = Ti> is implied.
            if (s.isInferenceType() || s.isProper()) {
                TypeMirror tType = s.getJavaType();
                for (AbstractType t : bounds.get(BoundKind.LOWER)) {
                    TypeMirror typeMirror;
                    if (t.isProper() || t.isInferenceType()) {
                        typeMirror = t.getJavaType();
                    } else {
                        continue;
                    }
                    Pair<TypeMirror, TypeMirror> pair =
                            InternalInferenceUtils.getParameterizedSupers(
                                    typeMirror, tType, context);
                    constraints.addAll(getConstraintsFromParameterized(pair, s, t));
                }
            }
        }
    }

    private List<Typing> getConstraintsFromParameterized(
            Pair<TypeMirror, TypeMirror> pair, AbstractType s, AbstractType t) {
        if (pair == null) {
            return new ArrayList<>();
        }
        List<Typing> constraints = new ArrayList<>();

        List<AbstractType> ss = s.asSuper(pair.first).getTypeArguments();
        List<AbstractType> ts = t.asSuper(pair.second).getTypeArguments();
        assert ss.size() == ts.size();

        for (int i = 0; i < ss.size(); i++) {
            AbstractType si = ss.get(i);
            AbstractType ti = ts.get(i);
            if (si.getTypeKind() != TypeKind.WILDCARD && ti.getTypeKind() != TypeKind.WILDCARD) {
                constraints.add(new Typing(si, ti, Constraint.Kind.TYPE_EQUALITY));
            }
        }
        return constraints;
    }

    public LinkedHashSet<ProperType> findProperLowerBounds() {
        LinkedHashSet<ProperType> set = new LinkedHashSet<>();
        for (AbstractType bound : bounds.get(BoundKind.LOWER)) {
            if (bound.isProper()) {
                set.add((ProperType) bound);
            }
        }
        return set;
    }

    public LinkedHashSet<ProperType> findProperUpperBounds() {
        LinkedHashSet<ProperType> set = new LinkedHashSet<>();
        for (AbstractType bound : bounds.get(BoundKind.UPPER)) {
            if (bound.isProper()) {
                set.add((ProperType) bound);
            }
        }
        return set;
    }

    public LinkedHashSet<AbstractType> upperBounds() {
        LinkedHashSet<AbstractType> set = new LinkedHashSet<>();
        for (AbstractType bound : bounds.get(BoundKind.UPPER)) {
            if (!bound.isVariable()) {
                set.add(bound);
            }
        }
        return set;
    }

    public boolean applyInstantiationsToBounds(List<Variable> instantiations) {
        boolean changed = false;
        for (Set<AbstractType> boundList : bounds.values()) {
            LinkedHashSet<AbstractType> newBounds = new LinkedHashSet<>(boundList.size());
            for (AbstractType bound : boundList) {
                AbstractType newBound = bound.applyInstantiations(instantiations);
                if (newBound != bound && !boundList.contains(newBound)) {
                    changed = true;
                }
                newBounds.add(newBound);
            }
            boundList.clear();
            boundList.addAll(newBounds);
        }
        constraints.applyInstantiations(instantiations);

        if (changed && instantiation == null) {
            for (AbstractType bound : bounds.get(BoundKind.EQUAL)) {
                if (bound.isProper()) {
                    instantiation = (ProperType) bound;
                }
            }
        }
        return changed;
    }

    public Collection<? extends Variable> getAllMentionedVars() {
        List<Variable> mentioned = new ArrayList<>();
        for (Set<AbstractType> boundList : bounds.values()) {
            for (AbstractType bound : boundList) {
                mentioned.addAll(bound.getInferenceVariables());
            }
        }
        return mentioned;
    }

    public ProperType getInstantiation() {
        return instantiation;
    }

    public boolean hasInstantiation() {
        return instantiation != null;
    }

    public boolean hasPrimitiveWrapperBound() {
        for (Set<AbstractType> boundList : bounds.values()) {
            for (AbstractType bound : boundList) {
                if (bound.isProper() && TypesUtils.isBoxedPrimitive(bound.getJavaType())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasWildcardParameterizedLowerOrEqualBound() {
        for (AbstractType type : bounds.get(BoundKind.EQUAL)) {
            if (!type.isVariable() && type.isWildcardParameterizedType()) {
                return true;
            }
        }
        for (AbstractType type : bounds.get(BoundKind.LOWER)) {
            if (!type.isVariable() && type.isWildcardParameterizedType()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does this bound set contain two bounds of the forms {@code S1 <: var} and {@code S2 <: var},
     * where S1 and S2 have supertypes that are two different parameterizations of the same generic
     * class or interface?
     */
    public boolean hasLowerBoundDifferentParam() {
        List<AbstractType> parameteredTypes = new ArrayList<>();
        for (AbstractType type : bounds.get(BoundKind.LOWER)) {
            if (!type.isVariable() && type.isParameterizedType()) {
                parameteredTypes.add(type);
            }
        }
        for (int i = 0; i < parameteredTypes.size(); i++) {
            AbstractType s1 = parameteredTypes.get(i);
            TypeMirror s1Java = s1.getJavaType();
            for (int j = i + 1; j < parameteredTypes.size(); j++) {
                AbstractType s2 = parameteredTypes.get(j);
                TypeMirror s2Java = s2.getJavaType();
                Pair<TypeMirror, TypeMirror> supers =
                        InternalInferenceUtils.getParameterizedSupers(s1Java, s2Java, context);
                if (supers == null) {
                    continue;
                }
                List<AbstractType> s1TypeArgs = s1.asSuper(supers.first).getTypeArguments();
                List<AbstractType> s2TypeArgs = s2.asSuper(supers.second).getTypeArguments();
                if (!s1TypeArgs.equals(s2TypeArgs)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Does this bound set contain a bound of one of the forms {@code var = S} or {@code S <: var},
     * where there exists no type of the form {@code G<...>} that is a supertype of S, but the raw
     * type {@code |G<...>|} is a supertype of S?
     */
    public boolean hasRawTypeLowerOrEqualBound(AbstractType g) {
        TypeMirror gTypeMirror = g.getJavaType();
        for (AbstractType type : bounds.get(BoundKind.LOWER)) {
            if (type.isVariable()) {
                continue;
            }
            AbstractType superTypeOfS = type.asSuper(gTypeMirror);
            if (superTypeOfS != null && superTypeOfS.isRaw()) {
                return true;
            }
        }

        for (AbstractType type : bounds.get(BoundKind.EQUAL)) {
            if (type.isVariable()) {
                continue;
            }
            AbstractType superTypeOfS = type.asSuper(gTypeMirror);
            if (superTypeOfS != null && superTypeOfS.isRaw()) {
                return true;
            }
        }
        return false;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Unsupported opps">

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Iterator<ProperType> getTypeParameterBounds() {
        return null;
    }

    // </editor-fold>

    public static class CaptureVariable extends Variable {

        public CaptureVariable(TypeVariable type, ExpressionTree invocation, Context context) {
            super(type, invocation, context, context.getNextCaputerVariableId());
        }

        @Override
        public String toString() {
            if (hasInstantiation()) {
                return "b" + id + " := " + getInstantiation();
            }
            return "b" + id;
        }

        /** These are constraints generated when incorporating a capture bound. */
        public ConstraintSet getWildcardConstraints(AbstractType Ai, AbstractType Bi) {
            ConstraintSet constraintSet = new ConstraintSet();

            // Only concerned with bounds against proper types or inference types.
            List<AbstractType> upperBoundsNonVar = new ArrayList<>();
            for (AbstractType bound : bounds.get(BoundKind.UPPER)) {
                if (bound.isProper() || bound.isInferenceType()) {
                    upperBoundsNonVar.add(bound);
                }
            }
            List<AbstractType> lowerBoundsNonVar = new ArrayList<>();
            for (AbstractType bound : bounds.get(BoundKind.LOWER)) {
                if (bound.isProper() || bound.isInferenceType()) {
                    lowerBoundsNonVar.add(bound);
                }
            }

            for (AbstractType bound : bounds.get(BoundKind.EQUAL)) {
                if (bound.isProper() || bound.isInferenceType()) {
                    // var = R implies the bound false
                    return null;
                }
            }

            if (Ai.isUnboundWildcard()) {
                // R <: var implies the bound false
                if (!lowerBoundsNonVar.isEmpty()) {
                    return null;
                }
                // var <: R implies the constraint formula <Bi theta <: R>
            } else if (Ai.isUpperBoundedWildcard()) {
                // R <: var implies the bound false
                if (!lowerBoundsNonVar.isEmpty()) {
                    return null;
                }
                AbstractType T = Ai.getWildcardUpperBound();
                if (Bi.isObject()) {
                    // If Bi is Object, then var <: R implies the constraint formula <T <: R>
                    for (AbstractType r : upperBoundsNonVar) {
                        constraintSet.add(new Typing(T, r, Constraint.Kind.SUBTYPE));
                    }
                } else if (T.isObject()) {
                    // If T is Object, then var <: R implies the constraint formula <Bi theta <: R>
                    for (AbstractType r : upperBoundsNonVar) {
                        constraintSet.add(new Typing(Bi, r, Constraint.Kind.SUBTYPE));
                    }
                }
                // else no constraint
            } else {
                // Super bounded wildcard
                // var <: R implies the constraint formula <Bi theta <: R>
                for (AbstractType r : upperBoundsNonVar) {
                    constraintSet.add(new Typing(Bi, r, Constraint.Kind.SUBTYPE));
                }

                // R <: var implies the constraint formula <R <: T>
                AbstractType T = Ai.getWildcardLowerBound();
                for (AbstractType r : lowerBoundsNonVar) {
                    constraintSet.add(new Typing(r, T, Constraint.Kind.SUBTYPE));
                }
            }
            return constraintSet;
        }
    }
}
