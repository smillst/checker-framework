package org.checkerframework.framework.util.typeinference8.bound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.InferenceTypeUtil;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.Pair;

/**
 * {@code G<a1, ..., an> = capture(G<A1, ..., An>)}: The variables a1, ..., an represent the result
 * of capture conversion applied to {@code G<A1, ..., An>} (where A1, ..., An may be types or
 * wildcards and may mention inference variables).
 */
public class Capture extends Bound {
    /** {@code G<A1, ..., An>} */
    private final AbstractType capturedType;
    /** G */
    private final DeclaredType underlying;
    /**
     * The substitution [P1 := alpha1, ..., Pn := alphan] where P1, ..., Pn are the type parameters
     * of the underlying type G
     */
    private final Theta map;
    /** {@code G<a1, ..., an>} */
    private final InferenceType lhs;

    private final List<CaptureTuple> tuples = new ArrayList<>();

    public Capture(AbstractType capturedType) {
        this.capturedType = capturedType;
        if (capturedType.isInferenceType()) {
            InferenceType inferenceTypeG = (InferenceType) capturedType;
            underlying = (DeclaredType) inferenceTypeG.getType();
        } else {
            ProperType properTypeG = (ProperType) capturedType;
            underlying = (DeclaredType) properTypeG.getProperType();
        }
        TypeElement ele = InternalUtils.getTypeElement(underlying);
        map = new Theta();
        List<Pair<Variable, ProperType>> pairs = new ArrayList<>();
        for (TypeParameterElement pEle : ele.getTypeParameters()) {
            TypeVariable pl = (TypeVariable) pEle.asType();
            Variable al = new Variable(pl);
            map.put(pl, al);
            pairs.add(Pair.of(al, new ProperType(pl.getUpperBound())));
        }

        lhs = (InferenceType) InferenceTypeUtil.create(underlying, map);

        Iterator<AbstractType> args = lhs.getTypeArguments().iterator();
        for (Pair<Variable, ProperType> pair : pairs) {
            AbstractType Ai = args.next();
            Variable alaphi = pair.first;
            ProperType Bi = pair.second;
            tuples.add(CaptureTuple.of(alaphi, Ai, Bi));
        }
    }

    public AbstractType getCapturedType() {
        return capturedType;
    }

    @Override
    public Kind getKind() {
        return Kind.CAPTURE;
    }

    public SortedSet<Variable> getAllIVOnLHS() {
        return new TreeSet<>(map.values());
    }

    public SortedSet<Variable> getAllIVOnRHS() {
        SortedSet<Variable> set = new TreeSet<>();
        for (CaptureTuple tuple : tuples) {
            if (tuple.typeArg.getKind() == AbstractType.Kind.VARIABLE) {
                set.add((Variable) tuple.typeArg);
            }
        }
        return set;
    }

    public Theta getMap() {
        return map;
    }

    public List<Bound> getInitialBounds() {
        // 18.3.2  In addition, for all i (1 ≤ i ≤ n):
        List<Bound> bounds = new ArrayList<>();
        for (CaptureTuple t : tuples) {
            Variable alphaI = t.alpha;
            AbstractType Ai = t.typeArg;
            if (Ai.getTypeKind() != TypeKind.WILDCARD) {
                // If Ai is not a wildcard, then the bound αi = Ai is implied.
                Equal.create(alphaI, Ai);
            }
        }
        return bounds;
    }

    public List<CaptureTuple> getTuples() {
        return tuples;
    }

    public InferenceType getLHS() {
        return lhs;
    }

    public static class CaptureTuple {
        public final Variable alpha;
        public final AbstractType typeArg;
        public final ProperType bound;

        private CaptureTuple(Variable alpha, AbstractType typeArg, ProperType bound) {
            this.alpha = alpha;
            this.typeArg = typeArg;
            this.bound = bound;
        }

        public static CaptureTuple of(
                Variable alpha, AbstractType capturedTypeArg, ProperType bound) {
            return new CaptureTuple(alpha, capturedTypeArg, bound);
        }
    }
}
