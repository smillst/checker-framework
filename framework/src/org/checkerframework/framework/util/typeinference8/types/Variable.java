package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.bound.Instantiation;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InferenceUtils;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TypesUtils;

public class Variable extends AbstractType {
    protected final int id;
    protected final TypeVariable typeVariable;
    /** The expression for which this variable is being solved. */
    protected final ExpressionTree invocation;

    protected ProperType instantiation = null;
    protected final EnumMap<InferBound, Set<AbstractType>> bounds = new EnumMap<>(InferBound.class);

    protected final Context context;

    /** Constraints implied by complementary pairs of bounds. */
    public Queue<Typing> constraints = new LinkedList<>();

    public Variable(TypeVariable typeVariable, ExpressionTree invocation, Context context) {
        this(typeVariable, invocation, context, context.getNextVariableId());
    }

    private Variable(
            TypeVariable typeVariable, ExpressionTree invocation, Context context, int id) {
        assert typeVariable != null;
        this.typeVariable = typeVariable;
        this.invocation = invocation;
        this.context = context;
        this.id = id;
        bounds.put(InferBound.EQUAL, new LinkedHashSet<>());
        bounds.put(InferBound.UPPER, new LinkedHashSet<>());
        bounds.put(InferBound.LOWER, new LinkedHashSet<>());
    }

    protected EnumMap<InferBound, LinkedHashSet<AbstractType>> savedBounds = null;

    public void save() {
        savedBounds = new EnumMap<>(InferBound.class);
        savedBounds.put(InferBound.EQUAL, new LinkedHashSet<>(bounds.get(InferBound.EQUAL)));
        savedBounds.put(InferBound.UPPER, new LinkedHashSet<>(bounds.get(InferBound.UPPER)));
        savedBounds.put(InferBound.LOWER, new LinkedHashSet<>(bounds.get(InferBound.LOWER)));
    }

    public void restore() {
        assert savedBounds != null;
        instantiation = null;
        bounds.clear();
        bounds.put(InferBound.EQUAL, new LinkedHashSet<>(savedBounds.get(InferBound.EQUAL)));
        bounds.put(InferBound.UPPER, new LinkedHashSet<>(savedBounds.get(InferBound.UPPER)));
        bounds.put(InferBound.LOWER, new LinkedHashSet<>(savedBounds.get(InferBound.LOWER)));
        for (AbstractType t : bounds.get(InferBound.EQUAL)) {
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
                    addBound(InferBound.UPPER, t1);
                }
                break;
            default:
                AbstractType t1 = InferenceType.create(upperBound, map, context);
                addBound(InferBound.UPPER, t1);
                break;
        }
    }

    public TypeVariable getTypeVariable() {
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
    public AbstractType applyInstantiations(List<Instantiation> instantiations) {
        for (Instantiation inst : instantiations) {
            if (inst.getA().equals(this)) {
                return inst.getT();
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
    public enum InferBound {
        LOWER,
        UPPER,
        EQUAL;
    }

    public boolean addBound(InferBound kind, AbstractType type) {
        if (kind == InferBound.EQUAL && type.isProper()) {
            instantiation = (ProperType) type;
        }
        if (bounds.get(kind).add(type)) {
            addConstraints(kind, type);
            return true;
        }
        return false;
    }

    private void addConstraints(InferBound kind, AbstractType s) {
        if (kind == InferBound.EQUAL) {
            for (AbstractType t : bounds.get(InferBound.EQUAL)) {
                if (s != t) {
                    constraints.add(new Typing(s, t, Typing.Kind.TYPE_EQUALITY));
                }
            }
        } else if (kind == InferBound.LOWER) {
            for (AbstractType t : bounds.get(InferBound.EQUAL)) {
                if (s != t) {
                    constraints.add(new Typing(s, t, Typing.Kind.SUBTYPE));
                }
            }
        } else { // UPPER
            for (AbstractType t : bounds.get(InferBound.EQUAL)) {
                if (s != t) {
                    constraints.add(new Typing(t, s, Typing.Kind.SUBTYPE));
                }
            }
        }

        if (kind == InferBound.EQUAL || kind == InferBound.UPPER) {
            for (AbstractType t : bounds.get(InferBound.LOWER)) {
                if (s != t) {
                    constraints.add(new Typing(t, s, Typing.Kind.SUBTYPE));
                }
            }
        }

        if (kind == InferBound.EQUAL || kind == InferBound.LOWER) {
            for (AbstractType t : bounds.get(InferBound.UPPER)) {
                if (s != t) {
                    constraints.add(new Typing(s, t, Typing.Kind.SUBTYPE));
                }
            }
        }

        if (kind == InferBound.UPPER) {
            // When a bound set contains a pair of bounds var <: S and var <: T, and there exists
            // a supertype of S of the form G<S1, ..., Sn> and
            // a supertype of T of the form G<T1,..., Tn> (for some generic class or interface, G),
            // then for all i (1 <= i <= n), if Si and Ti are types (not wildcards),
            // the constraint formula <Si = Ti> is implied.
            if (s.isInferenceType() || s.isProper()) {
                TypeMirror tType =
                        s.isInferenceType()
                                ? ((InferenceType) s).getType()
                                : ((ProperType) s).getProperType();
                for (AbstractType t : bounds.get(InferBound.LOWER)) {
                    TypeMirror typeMirror;
                    if (t.isProper()) {
                        typeMirror = ((ProperType) t).getProperType();
                    } else if (t.isInferenceType()) {
                        typeMirror = ((InferenceType) t).getType();
                    } else {
                        continue;
                    }
                    Pair<TypeMirror, TypeMirror> pair =
                            InternalInferenceUtils.getParameterizedSupers(
                                    context, typeMirror, tType);
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
        for (AbstractType bound : bounds.get(InferBound.LOWER)) {
            if (bound.isProper()) {
                set.add((ProperType) bound);
            }
        }
        return set;
    }

    public LinkedHashSet<ProperType> findProperUpperBounds() {
        LinkedHashSet<ProperType> set = new LinkedHashSet<>();
        for (AbstractType bound : bounds.get(InferBound.UPPER)) {
            if (bound.isProper()) {
                set.add((ProperType) bound);
            }
        }
        return set;
    }

    public boolean applyInstantiationsToBounds(List<Instantiation> instantiations) {
        boolean changed = false;
        for (Set<AbstractType> boundList : bounds.values()) {
            LinkedHashSet<AbstractType> newBounds = new LinkedHashSet<>(boundList.size());
            for (AbstractType bound : boundList) {
                AbstractType newBound = bound.applyInstantiations(instantiations);
                if (newBound != bound) {
                    changed = true;
                }
                newBounds.add(newBound);
            }
            boundList.clear();
            boundList.addAll(newBounds);
        }
        if (changed && instantiation == null) {
            for (AbstractType bound : bounds.get(InferBound.EQUAL)) {
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
                if (bound.isProper()
                        && TypesUtils.isBoxedPrimitive(((ProperType) bound).getProperType())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasWildcardParameterizedLowerOrEqualBound() {
        for (AbstractType type : bounds.get(InferBound.EQUAL)) {
            if (!type.isVariable() && type.isWildcardParameterizedType()) {
                return true;
            }
        }
        for (AbstractType type : bounds.get(InferBound.LOWER)) {
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
        for (AbstractType type : bounds.get(InferBound.LOWER)) {
            if (!type.isVariable() && type.isParameterizedType()) {
                parameteredTypes.add(type);
            }
        }
        for (int i = 0; i < parameteredTypes.size(); i++) {
            AbstractType s1 = parameteredTypes.get(i);
            TypeMirror s1Java = InferenceUtils.getJavaType(s1);
            for (int j = i + 1; j < parameteredTypes.size(); j++) {
                AbstractType s2 = parameteredTypes.get(j);
                TypeMirror s2Java = InferenceUtils.getJavaType(s2);
                Pair<TypeMirror, TypeMirror> supers =
                        InternalInferenceUtils.getParameterizedSupers(context, s1Java, s2Java);
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
        TypeMirror gTypeMirror =
                g.isInferenceType()
                        ? ((InferenceType) g).getType()
                        : ((ProperType) g).getProperType();
        for (AbstractType type : bounds.get(InferBound.LOWER)) {
            if (type.isVariable()) {
                continue;
            }
            AbstractType superTypeOfS = type.asSuper(gTypeMirror);
            if (superTypeOfS != null && superTypeOfS.isRaw()) {
                return true;
            }
        }

        for (AbstractType type : bounds.get(InferBound.UPPER)) {
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
    public TypeKind getTypeKind() {
        return TypeKind.TYPEVAR;
    }

    @Override
    public AbstractType asSuper(TypeMirror superType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public boolean isParameterizedType() {
        return false;
    }

    @Override
    public AbstractType getMostSpecificArrayType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPrimitiveArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUpperBoundedWildcard() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AbstractType> getIntersectionBounds() {
        return null;
    }

    @Override
    public AbstractType getTypeVarLowerBound() {
        return null;
    }

    @Override
    public boolean hasLowerBound() {
        return false;
    }

    @Override
    public Iterator<ProperType> getTypeParameterBounds() {
        return null;
    }

    @Override
    public AbstractType replaceTypeArgs(List<AbstractType> ts) {
        return null;
    }

    @Override
    public boolean isWildcardParameterizedType() {
        return false;
    }

    @Override
    public AbstractType getFunctionTypeReturn() {
        return null;
    }

    @Override
    public boolean isRaw() {
        return false;
    }

    @Override
    public List<AbstractType> getFunctionTypeParameters() {
        return null;
    }

    @Override
    public List<AbstractType> getTypeArguments() {
        return null;
    }

    @Override
    public AbstractType getComponentType() {
        return null;
    }

    @Override
    public AbstractType getWildcardUpperBound() {
        return null;
    }

    @Override
    public AbstractType getWildcardLowerBound() {
        return null;
    }

    @Override
    public boolean isUnboundWildcard() {
        return false;
    }

    @Override
    public boolean isLowerBoundedWildcard() {
        return false;
    }
    // </editor-fold>

    /**
     * @link com.sun.tools.javac.code.Type.CapturedUndetVar}: The only difference between these
     *     inference variables and ordinary ones is that captured inference variables cannot get new
     *     bounds through incorporation.
     */
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

        /** Returns null if the bound false is implied. */
        public ConstraintSet getWildcardConstraints(AbstractType Ai, AbstractType Bi) {
            ConstraintSet constraintSet = new ConstraintSet();

            // Only concerned with bounds against proper types or inference types.
            List<AbstractType> upperBoundsNonVar = new ArrayList<>();
            for (AbstractType bound : bounds.get(InferBound.UPPER)) {
                if (bound.isProper() || bound.isInferenceType()) {
                    upperBoundsNonVar.add(bound);
                }
            }
            List<AbstractType> lowerBoundsNonVar = new ArrayList<>();
            for (AbstractType bound : bounds.get(InferBound.LOWER)) {
                if (bound.isProper() || bound.isInferenceType()) {
                    lowerBoundsNonVar.add(bound);
                }
            }

            for (AbstractType bound : bounds.get(InferBound.EQUAL)) {
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
                // var <: R implies the constraint formula ‹Bi θ <: R›
            } else if (Ai.isUpperBoundedWildcard()) {
                // R <: var implies the bound false
                if (!lowerBoundsNonVar.isEmpty()) {
                    return null;
                }
                AbstractType T = Ai.getWildcardUpperBound();
                if (Bi.isObject()) {
                    // If Bi is Object, then var <: R implies the constraint formula ‹T <: R›
                    for (AbstractType r : upperBoundsNonVar) {
                        constraintSet.add(new Typing(T, r, Constraint.Kind.SUBTYPE));
                    }
                } else if (T.isObject()) {
                    // If T is Object, then var <: R implies the constraint formula ‹Bi θ <: R›
                    for (AbstractType r : upperBoundsNonVar) {
                        constraintSet.add(new Typing(Bi, r, Constraint.Kind.SUBTYPE));
                    }
                }
                // else no constraint
            } else {
                // Super bounded wildcard
                // var <: R implies the constraint formula ‹Bi θ <: R›
                for (AbstractType r : upperBoundsNonVar) {
                    constraintSet.add(new Typing(Bi, r, Constraint.Kind.SUBTYPE));
                }

                // R <: var implies the constraint formula ‹R <: T›
                AbstractType T = Ai.getWildcardLowerBound();
                for (AbstractType r : lowerBoundsNonVar) {
                    constraintSet.add(new Typing(r, T, Constraint.Kind.SUBTYPE));
                }
            }
            return constraintSet;
        }
    }
}
