package org.checkerframework.framework.util.typeinference8.resolution;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.types.Dependencies;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.types.Variable.InferBound;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.FalseBoundException;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;

public class Resolution {
    public static BoundSet resolve(List<Variable> as, BoundSet boundSet, Context context) {
        List<Variable> resolvedVars = boundSet.getInstantiatedVariables();

        if (as.isEmpty()) {
            return boundSet;
        }
        Dependencies dependencies = boundSet.getDependencies();
        Queue<Variable> unresolvedVars = new LinkedList<>(as);
        for (Variable var : as) {
            for (Variable dep : dependencies.get(var)) {
                if (!unresolvedVars.contains(dep)) {
                    unresolvedVars.add(dep);
                }
            }
        }

        unresolvedVars.removeAll(resolvedVars);
        if (unresolvedVars.isEmpty()) {
            return boundSet;
        }

        Resolution resolution = new Resolution(context, dependencies, resolvedVars);
        boundSet = resolution.resolve(boundSet, unresolvedVars);
        assert !boundSet.containsFalse();
        return boundSet;
    }

    public static BoundSet resolve(Variable a, BoundSet boundSet, Context context) {

        Dependencies dependencies = boundSet.getDependencies();

        List<Variable> resolvedVars = boundSet.getInstantiatedVariables();
        LinkedHashSet<Variable> unresolvedVars = new LinkedHashSet<>();
        unresolvedVars.add(a);
        Resolution resolution = new Resolution(context, dependencies, resolvedVars);
        boundSet = resolution.resolve(unresolvedVars, boundSet);
        assert !boundSet.containsFalse();
        return boundSet;
    }

    private final Context context;
    private final Dependencies dependencies;
    private List<Variable> resolvedVars;

    public Resolution(Context context, Dependencies dependencies, List<Variable> resolvedVars) {
        this.context = context;
        this.dependencies = dependencies;
        this.resolvedVars = resolvedVars;
    }

    public BoundSet resolve(BoundSet boundSet, Queue<Variable> unresolvedVars) {

        while (!unresolvedVars.isEmpty()) {
            LinkedHashSet<Variable> smallestDependencySet = null;
            // This loop is looking for the smallest set of dependencies that have not been resolved.
            for (Variable alpha : unresolvedVars) {
                LinkedHashSet<Variable> alphasDependencySet = dependencies.get(alpha);
                alphasDependencySet.removeAll(resolvedVars);

                if (smallestDependencySet == null
                        || alphasDependencySet.size() < smallestDependencySet.size()) {
                    smallestDependencySet = alphasDependencySet;
                }

                if (smallestDependencySet.size() == 1) {
                    // If the size is 1, then alpha has the smallest possible set of unresolved dependencies.
                    // (A variable is always dependent on itself.) So, stop looking for smaller ones.
                    break;
                }
            }

            // Resolve the smallest unresolved dependency set.
            boundSet = resolve(smallestDependencySet, boundSet);
            resolvedVars = boundSet.getInstantiatedVariables();
            unresolvedVars.removeAll(resolvedVars);
        }
        return boundSet;
    }

    private BoundSet resolve(LinkedHashSet<Variable> as, BoundSet boundSet) {
        assert !boundSet.containsFalse();

        BoundSet resolvedBounds;
        if (boundSet.containsCapture(as)) {
            resolvedBounds = resolve2(as, boundSet, context);
        } else {
            BoundSet copy = new BoundSet(boundSet);
            try {
                resolvedBounds = resolve1(as, boundSet);
            } catch (FalseBoundException ex) {
                resolvedBounds = null;
            }
            if (resolvedBounds == null || resolvedBounds.containsFalse()) {
                boundSet = copy;
                boundSet.restore();
                resolvedBounds = resolve2(as, boundSet, context);
            }
        }
        return resolvedBounds;
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.4-320-A */
    private BoundSet resolve1(LinkedHashSet<Variable> as, BoundSet boundSet) {
        BoundSet resolvedBoundSet = new BoundSet(context);
        for (Variable ai : as) {
            assert !ai.hasInstantiation();
            LinkedHashSet<ProperType> lowerBounds = ai.findProperLowerBounds();
            if (!lowerBounds.isEmpty()) {
                TypeMirror ti = null;
                for (ProperType liProperType : lowerBounds) {
                    TypeMirror li = liProperType.getProperType();
                    if (ti == null) {
                        ti = li;
                    } else {
                        ti = InternalInferenceUtils.lub(context.env, ti, li);
                    }
                }
                ai.addBound(InferBound.EQUAL, new ProperType(ti, context));
                continue;
            }

            LinkedHashSet<ProperType> upperBounds = ai.findProperUpperBounds();
            if (!upperBounds.isEmpty()) {
                TypeMirror ti = null;
                boolean useRuntimeEx = false;
                for (ProperType liProperType : upperBounds) {
                    TypeMirror li = liProperType.getProperType();
                    if (ai.hasThrowsBound()
                            && context.env.getTypeUtils().isSubtype(context.runtimeEx, li)) {
                        useRuntimeEx = true;
                    }
                    if (ti == null) {
                        ti = li;
                    } else {
                        ti = InternalInferenceUtils.glb(context.env, ti, li);
                    }
                }
                if (useRuntimeEx) {
                    ai.addBound(InferBound.EQUAL, new ProperType(context.runtimeEx, context));
                } else {
                    ai.addBound(InferBound.EQUAL, new ProperType(ti, context));
                }
                continue;
            }
            resolvedBoundSet.addFalse();
            break;
        }
        boundSet.incorporateToFixedPoint(resolvedBoundSet);

        return boundSet;
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.4-320-B */
    private static BoundSet resolve2(
            LinkedHashSet<Variable> as, BoundSet boundSet, Context context) {
        assert !boundSet.containsFalse();
        boundSet.removeCaptures(as);
        BoundSet resolvedBoundSet = new BoundSet(context);

        LinkedList<Variable> queque = new LinkedList<>();
        for (Variable ai : as) {
            if (ai.isCaptureVariable()) {
                queque.add(ai);
            } else {
                queque.addFirst(ai);
            }
        }

        for (Variable ai : queque) {
            ai.applyInstantiationsToBounds(boundSet.getInstantiationsAll());
            if (ai.hasInstantiation()) {
                // If ai is equal to a variable that was resolved in the last loop,
                // ai would now have an instantiation.
                continue;
            }
            LinkedHashSet<ProperType> lowerBounds = ai.findProperLowerBounds();
            TypeMirror lowerBound = null;
            if (!lowerBounds.isEmpty()) {
                for (ProperType liProperType : lowerBounds) {
                    TypeMirror li = liProperType.getProperType();
                    if (lowerBound == null) {
                        lowerBound = li;
                    } else {
                        lowerBound = InternalInferenceUtils.lub(context.env, lowerBound, li);
                    }
                }
            }

            LinkedHashSet<ProperType> upperBounds = ai.findProperUpperBounds();
            TypeMirror upperBound = null;
            if (!upperBounds.isEmpty()) {
                for (ProperType liProperType : upperBounds) {
                    TypeMirror li = liProperType.getProperType();
                    if (upperBound == null) {
                        upperBound = li;
                    } else {
                        upperBound = InternalInferenceUtils.glb(context.env, upperBound, li);
                    }
                }
            }
            if (ai.isCaptureVariable()) {
                // TODO: This won't square with the capture that javac produces.
                TypeMirror freshTypeVar =
                        InternalInferenceUtils.getFreshTypeVar(context, lowerBound, upperBound);
                // TODO: This might contain other inference varibles.
                ai.addBound(InferBound.EQUAL, new ProperType(freshTypeVar, context));
            } else if (lowerBound != null) {
                ai.addBound(InferBound.EQUAL, new ProperType(lowerBound, context));
            } else {
                ai.addBound(InferBound.EQUAL, new ProperType(upperBound, context));
            }
        }
        boundSet.incorporateToFixedPoint(resolvedBoundSet);

        return boundSet;
    }
}
