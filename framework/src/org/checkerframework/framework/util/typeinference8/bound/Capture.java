package org.checkerframework.framework.util.typeinference8.bound;

import com.sun.source.tree.ExpressionTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.types.Variable.CaptureVariable;
import org.checkerframework.framework.util.typeinference8.types.Variable.InferBound;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TypesUtils;

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

    private final List<CaptureVariable> captureVariables = new ArrayList<>();

    public Capture(AbstractType capturedType, ExpressionTree tree, Context context) {
        this.capturedType = capturedType;
        if (capturedType.isInferenceType()) {
            InferenceType inferenceTypeG = (InferenceType) capturedType;
            underlying = (DeclaredType) inferenceTypeG.getType();
        } else {
            ProperType properTypeG = (ProperType) capturedType;
            underlying = (DeclaredType) properTypeG.getProperType();
        }
        TypeElement ele = TypesUtils.getTypeElement(underlying);
        map = new Theta();
        List<Pair<CaptureVariable, TypeMirror>> pairs = new ArrayList<>();
        for (TypeParameterElement pEle : ele.getTypeParameters()) {
            TypeVariable pl = (TypeVariable) pEle.asType();
            CaptureVariable al = new CaptureVariable(pl, tree, context);
            map.put(pl, al);
            pairs.add(Pair.of(al, pl.getUpperBound()));
            captureVariables.add(al);
        }

        lhs = (InferenceType) InferenceType.create(ele.asType(), map, context);

        Iterator<AbstractType> args = capturedType.getTypeArguments().iterator();
        for (Pair<CaptureVariable, TypeMirror> pair : pairs) {
            AbstractType Ai = args.next();
            CaptureVariable alaphi = pair.first;
            alaphi.initialBounds(map);
            AbstractType Bi = InferenceType.create(pair.second, map, context);
            tuples.add(CaptureTuple.of(alaphi, Ai, Bi));
        }

        // 18.3.2  In addition, for all i (1 ≤ i ≤ n):
        for (CaptureTuple t : tuples) {
            Variable alphaI = t.alpha;
            AbstractType Ai = t.capturedTypeArg;
            if (Ai.getTypeKind() != TypeKind.WILDCARD) {
                // If Ai is not a wildcard, then the bound αi = Ai is implied.
                alphaI.addBound(InferBound.EQUAL, Ai);
            }
        }
    }

    public AbstractType getCapturedType() {
        return capturedType;
    }

    @Override
    public Kind getKind() {
        return Kind.CAPTURE;
    }

    public List<? extends CaptureVariable> getAllIVOnLHS() {
        return captureVariables;
    }

    public LinkedHashSet<Variable> getAllIVOnRHS() {
        LinkedHashSet<Variable> set = new LinkedHashSet<>();
        for (CaptureTuple tuple : tuples) {
            if (tuple.capturedTypeArg.getKind() == AbstractType.Kind.VARIABLE) {
                set.add((Variable) tuple.capturedTypeArg);
            }
        }
        return set;
    }

    public Theta getMap() {
        return map;
    }

    public List<CaptureTuple> getTuples() {
        return tuples;
    }

    public InferenceType getLHS() {
        return lhs;
    }

    @Override
    public boolean isCaptureMentionsAny(Collection<Variable> as) {
        for (Variable a : as) {
            if (map.containsValue(a)) {
                return true;
            }
        }
        return false;
    }

    public static class CaptureTuple {

        /**
         * Fresh inference variable (in the left hand side of the capture). (Also referred to as
         * beta in the some places in the JLS.) For example {@code a1} in {@code G<a1, ..., an> =
         * capture(G<A1, ..., An>)}.
         */
        public final CaptureVariable alpha;
        /**
         * Type argument in the right hand side for the capture. For example {@code A1} in {@code
         * G<a1, ..., an> = capture(G<A1, ..., An>)}.
         */
        public final AbstractType capturedTypeArg;

        /**
         * Upper bound of one of the type parameters of G that has been substituted using the fresh
         * inference variables.
         */
        public final AbstractType bound;

        private CaptureTuple(
                CaptureVariable alpha, AbstractType capturedTypeArg, AbstractType bound) {
            this.alpha = alpha;
            this.capturedTypeArg = capturedTypeArg;
            this.bound = bound;
        }

        public static CaptureTuple of(
                CaptureVariable alpha, AbstractType capturedTypeArg, AbstractType bound) {
            return new CaptureTuple(alpha, capturedTypeArg, bound);
        }
    }
}
