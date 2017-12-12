package org.checkerframework.framework.util.typeinference8.bound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.PluginUtil;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Kind;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TypesUtils;

public class BoundsForVar {

    public final Variable var;
    private final VarBounds upperBounds;
    private final VarBounds lowerBounds;
    private final VarBounds equalBounds;

    private final LinkedHashSet<Constraint> toIncorporate = new LinkedHashSet<>();
    private final LinkedHashSet<Constraint> previsoulyIncorporated = new LinkedHashSet<>();
    private final Context context;

    public BoundsForVar(Variable var, Context context) {
        this.var = var;
        this.context = context;
        this.upperBounds = new VarBounds();
        this.lowerBounds = new VarBounds();
        this.equalBounds = new VarBounds();
    }

    public BoundsForVar(BoundsForVar toCopy) {
        this.var = toCopy.var;
        this.context = toCopy.context;
        this.upperBounds = new VarBounds(toCopy.upperBounds);
        this.lowerBounds = new VarBounds(toCopy.lowerBounds);
        this.equalBounds = new VarBounds(toCopy.equalBounds);
        this.toIncorporate.addAll(toCopy.toIncorporate);
    }

    /**
     * Add bound {@code var = s }. Also, adds constraints implied by the bounds to {@link
     * #toIncorporate} unless s is a proper type. In that case the constraints are added else where.
     *
     * @return true var = s is a new bound.
     */
    public boolean addEqual(AbstractType s) {
        if (equalBounds.contains(s)) {
            return false;
        }

        if (s.isProper()) {
            assert equalBounds.properTypes.isEmpty();
        }

        // Equal Constraints
        // a = S and a = T imply <S = T>
        for (AbstractType t : equalBounds.getAll()) {
            toIncorporate.add(new Typing(s, t, Kind.TYPE_EQUALITY));
        }

        // Upper Bound Constraints
        // a = S and a <: T imply <S <: T>
        for (AbstractType t : upperBounds.getAll()) {
            toIncorporate.add(new Typing(s, t, Kind.SUBTYPE));
        }

        // Lower Bound Constraints
        // a = S and T <: a imply <T <: S>
        for (AbstractType t : lowerBounds.getAll()) {
            toIncorporate.add(new Typing(t, s, Kind.SUBTYPE));
        }
        equalBounds.add(s);
        return true;
    }

    /**
     * Add bound {@code var <: t}. Also, adds constraints implied by the bounds to {@link
     * #toIncorporate}.
     *
     * @return true if {@code var <: t} is a new bound.
     */
    public boolean addUpperBound(AbstractType t) {
        if (upperBounds.contains(t)) {
            return false;
        }

        // Lower Bound Constraints
        // a = S and a <: T imply <S <: T>
        for (AbstractType s : equalBounds.getAll()) {
            toIncorporate.add(new Typing(s, t, Kind.SUBTYPE));
        }

        // Lower Bound Constraints
        // S <: a and a <: T imply <S <: T>
        for (AbstractType s : lowerBounds.getAll()) {
            toIncorporate.add(new Typing(s, t, Kind.SUBTYPE));
        }

        // When a bound set contains a pair of bounds var <: S and var <: T, and there exists
        // a supertype of S of the form G<S1, ..., Sn> and
        // a supertype of T of the form G<T1,..., Tn> (for some generic class or interface, G),
        // then for all i (1 <= i <= n), if Si and Ti are types (not wildcards),
        // the constraint formula <Si = Ti> is implied.
        if (t.isInferenceType() || t.isProper()) {
            TypeMirror tType = t.getJavaType();
            for (ProperType s : upperBounds.properTypes) {
                Pair<TypeMirror, TypeMirror> pair =
                        InternalInferenceUtils.getParameterizedSupers(
                                context, s.getJavaType(), tType);
                toIncorporate.addAll(getConstraintsFromParameterized(pair, s, t));
            }

            for (InferenceType s : upperBounds.inferenceTypes) {
                Pair<TypeMirror, TypeMirror> pair =
                        InternalInferenceUtils.getParameterizedSupers(
                                context, s.getJavaType(), tType);
                toIncorporate.addAll(getConstraintsFromParameterized(pair, s, t));
            }
        }

        upperBounds.add(t);
        return true;
    }

    private LinkedHashSet<Constraint> getConstraintsFromParameterized(
            Pair<TypeMirror, TypeMirror> pair, AbstractType s, AbstractType t) {
        if (pair == null) {
            return new LinkedHashSet<>();
        }
        LinkedHashSet<Constraint> constraints = new LinkedHashSet<>();

        List<AbstractType> ss = s.asSuper(pair.first).getTypeArguments();
        List<AbstractType> ts = t.asSuper(pair.second).getTypeArguments();
        assert ss.size() == ts.size();

        for (int i = 0; i < ss.size(); i++) {
            AbstractType si = ss.get(i);
            AbstractType ti = ts.get(i);
            if (si.getTypeKind() != TypeKind.WILDCARD && ti.getTypeKind() != TypeKind.WILDCARD) {
                constraints.add(new Typing(si, ti, Kind.TYPE_EQUALITY));
            }
        }
        return constraints;
    }

    /** {@code t <: var} */
    public boolean addLowerBound(AbstractType t) {
        if (lowerBounds.contains(t)) {
            return false;
        }

        // Subtyping Constraints:
        //  T <: a and a = S imply <T <: S>
        for (AbstractType s : equalBounds.getAll()) {
            toIncorporate.add(new Typing(t, s, Kind.SUBTYPE));
        }

        // Lower Bound Constraints
        // T <: a and a <: S imply <T <: S>
        for (AbstractType s : upperBounds.getAll()) {
            toIncorporate.add(new Typing(t, s, Kind.SUBTYPE));
        }

        lowerBounds.add(t);
        return true;
    }

    public boolean hasProperUpperBound() {
        return !upperBounds.properTypes.isEmpty();
    }

    public LinkedHashSet<ProperType> getProperLowerBounds() {
        return lowerBounds.properTypes;
    }

    public LinkedHashSet<ProperType> getProperUpperBounds() {
        return upperBounds.properTypes;
    }

    public Collection<? extends Variable> getAllMentionedVars() {
        List<Variable> list = new ArrayList<>();
        for (AbstractType t : lowerBounds.getAll()) {
            list.addAll(t.getInferenceVariables());
        }

        for (AbstractType t : upperBounds.getAll()) {
            list.addAll(t.getInferenceVariables());
        }

        for (AbstractType t : equalBounds.getAll()) {
            list.addAll(t.getInferenceVariables());
        }

        return list;
    }

    public ProperType getInstantiation() {
        if (equalBounds.properTypes.size() != 1) {
            ErrorReporter.errorAbort("Should be only one instantiation.");
        }
        return equalBounds.properTypes.iterator().next();
    }

    public boolean hasInstantiation() {
        return equalBounds.properTypes.size() == 1;
    }

    public ConstraintSet getConstraintFromComplementaryBounds() {
        ConstraintSet constraintSet = new ConstraintSet(toIncorporate.toArray(new Constraint[0]));
        previsoulyIncorporated.addAll(toIncorporate);
        toIncorporate.clear();
        return constraintSet;
    }

    public void applyInstantiations(List<Instantiation> instantiations) {
        if (instantiations.isEmpty()) {
            return;
        }
        // var = T imply <varPrime = T[alpha:=U]›
        for (AbstractType T : equalBounds.getAll()) {
            AbstractType tPrime = T.applyInstantiations(instantiations);
            if (T != tPrime) {
                equalBounds.replace(T, tPrime);
            }
        }

        // var <: T imply <varPrime <: T[alpha:=U]›
        for (AbstractType T : upperBounds.getAll()) {
            AbstractType tPrime = T.applyInstantiations(instantiations);
            if (T != tPrime) {
                upperBounds.replace(T, tPrime);
            }
        }

        // S <: var imply <S[alpha:=U] <: varPrime]›
        for (AbstractType s : lowerBounds.getAll()) {
            AbstractType sPrime = s.applyInstantiations(instantiations);
            if (s != sPrime) {
                lowerBounds.replace(s, sPrime);
            }
        }
    }

    public boolean merge(BoundsForVar other) {
        boolean changed = false;

        for (AbstractType t : other.equalBounds.getAll()) {
            changed |= this.addEqual(t);
        }
        for (AbstractType t : other.lowerBounds.getAll()) {
            changed |= this.addLowerBound(t);
        }
        for (AbstractType t : other.upperBounds.getAll()) {
            changed |= this.addUpperBound(t);
        }

        if (changed) {
            this.toIncorporate.addAll(other.toIncorporate);
            this.previsoulyIncorporated.addAll(other.previsoulyIncorporated);
        } else {
            if (!this.previsoulyIncorporated.containsAll(other.toIncorporate)) {
                changed = this.toIncorporate.addAll(other.toIncorporate);
                changed |= this.previsoulyIncorporated.addAll(other.previsoulyIncorporated);
            }
        }

        return changed;
    }

    /** Returns null if the bound false is implied. */
    public ConstraintSet getWildcardConstraints(AbstractType Ai, AbstractType Bi) {
        ConstraintSet constraintSet = new ConstraintSet();

        // Only concerned with bounds against proper types or inference types.
        List<AbstractType> upperBoundsNonVar = new ArrayList<>();
        upperBoundsNonVar.addAll(upperBounds.inferenceTypes);
        upperBoundsNonVar.addAll(upperBounds.properTypes);

        List<AbstractType> lowerBoundsNonVar = new ArrayList<>();
        lowerBoundsNonVar.addAll(lowerBounds.inferenceTypes);
        lowerBoundsNonVar.addAll(lowerBounds.properTypes);

        List<AbstractType> equalNonVar = new ArrayList<>();
        equalNonVar.addAll(equalBounds.inferenceTypes);
        equalNonVar.addAll(equalBounds.properTypes);

        if (!equalNonVar.isEmpty()) {
            // var = R implies the bound false
            return null;
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
                    constraintSet.add(new Typing(T, r, Kind.SUBTYPE));
                }
            } else if (T.isObject()) {
                // If T is Object, then var <: R implies the constraint formula ‹Bi θ <: R›
                for (AbstractType r : upperBoundsNonVar) {
                    constraintSet.add(new Typing(Bi, r, Kind.SUBTYPE));
                }
            }
            // else no constraint
        } else {
            // Super bounded wildcard
            // var <: R implies the constraint formula ‹Bi θ <: R›
            for (AbstractType r : upperBoundsNonVar) {
                constraintSet.add(new Typing(Bi, r, Kind.SUBTYPE));
            }

            // R <: var implies the constraint formula ‹R <: T›
            AbstractType T = Ai.getWildcardLowerBound();
            for (AbstractType r : lowerBoundsNonVar) {
                constraintSet.add(new Typing(r, T, Kind.SUBTYPE));
            }
        }
        return constraintSet;
    }

    public boolean hasPrimitiveWrapperBound() {
        if (hasInstantiation()) {
            if (TypesUtils.isBoxedPrimitive(getInstantiation().getJavaType())) {
                return true;
            }
        }
        for (ProperType type : getProperLowerBounds()) {
            if (TypesUtils.isBoxedPrimitive(type.getJavaType())) {
                return true;
            }
        }

        for (ProperType type : getProperUpperBounds()) {
            if (TypesUtils.isBoxedPrimitive(type.getJavaType())) {
                return true;
            }
        }

        return false;
    }

    public boolean hasWildcardParameterizedLowerOrEqualBound() {
        for (AbstractType type : equalBounds.getAll()) {
            if (!type.isVariable() && type.isWildcardParameterizedType()) {
                return true;
            }
        }
        for (AbstractType type : lowerBounds.getAll()) {
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
        for (AbstractType type : lowerBounds.getAll()) {
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
        TypeMirror gTypeMirror = g.getJavaType();
        for (AbstractType type : lowerBounds.getAll()) {
            if (type.isVariable()) {
                continue;
            }
            AbstractType superTypeOfS = type.asSuper(gTypeMirror);
            if (superTypeOfS != null && superTypeOfS.isRaw()) {
                return true;
            }
        }

        for (AbstractType type : equalBounds.getAll()) {
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

    public boolean containsUnincorporated() {
        return !toIncorporate.isEmpty();
    }

    private static class VarBounds {
        final LinkedHashSet<ProperType> properTypes;
        final LinkedHashSet<Variable> variables;
        final LinkedHashSet<InferenceType> inferenceTypes;

        VarBounds() {
            this.properTypes = new LinkedHashSet<>();
            this.variables = new LinkedHashSet<>();
            this.inferenceTypes = new LinkedHashSet<>();
        }

        public VarBounds(VarBounds toCopy) {
            this();
            properTypes.addAll(toCopy.properTypes);
            variables.addAll(toCopy.variables);
            inferenceTypes.addAll(toCopy.inferenceTypes);
        }

        boolean add(AbstractType t) {
            assert t != null;
            switch (t.getKind()) {
                case PROPER:
                    return properTypes.add((ProperType) t);
                case VARIABLE:
                    return variables.add((Variable) t);
                case INFERENCE_TYPE:
                    return inferenceTypes.add((InferenceType) t);
                default:
                    ErrorReporter.errorAbort("Unexpected type");
                    return false;
            }
        }

        private List<AbstractType> getAllNonProper() {
            List<AbstractType> list = new ArrayList<>();
            list.addAll(variables);
            list.addAll(inferenceTypes);
            return list;
        }

        private List<AbstractType> getAll() {
            List<AbstractType> list = new ArrayList<>();
            list.addAll(properTypes);
            list.addAll(variables);
            list.addAll(inferenceTypes);
            return list;
        }

        private boolean contains(AbstractType t) {
            switch (t.getKind()) {
                case PROPER:
                    return properTypes.contains(t);
                case VARIABLE:
                    return variables.contains(t);
                case INFERENCE_TYPE:
                    return inferenceTypes.contains(t);
                default:
                    ErrorReporter.errorAbort("Unexpected type");
                    return false;
            }
        }

        public boolean isEmpty() {
            return variables.isEmpty() && properTypes.isEmpty() && inferenceTypes.isEmpty();
        }

        @Override
        public String toString() {
            return PluginUtil.join("\n", getAll());
        }

        public String toStringSingleLine() {
            return PluginUtil.join(" ", getAll());
        }

        public void replace(AbstractType oldBound, AbstractType newBound) {
            switch (oldBound.getKind()) {
                case PROPER:
                    properTypes.remove(oldBound);
                    break;
                case VARIABLE:
                    variables.remove(oldBound);
                    break;
                case INFERENCE_TYPE:
                    inferenceTypes.remove(oldBound);
                    break;
                default:
                    ErrorReporter.errorAbort("Unexpected type");
            }
            add(newBound);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (equalBounds.properTypes.size() == 1) {
            builder.append(var).append(" := ").append(equalBounds.properTypes.iterator().next());
        } else {
            builder.append(var);
        }
        builder.append(" {");
        if (!upperBounds.isEmpty()) {
            builder.append(" upperBounds = ").append(upperBounds.toStringSingleLine());
        }
        if (!lowerBounds.isEmpty()) {
            builder.append(" lowerBounds = ").append(lowerBounds.toStringSingleLine());
        }
        if (!equalBounds.isEmpty()) {
            builder.append(" equalBounds = ").append(equalBounds.toStringSingleLine());
        }
        if (!toIncorporate.isEmpty()) {
            builder.append("\ntoIncorporate = ").append(toIncorporate);
        }
        builder.append("}");
        return builder.toString();
    }
}
