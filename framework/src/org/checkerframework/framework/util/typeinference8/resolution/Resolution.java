package org.checkerframework.framework.util.typeinference8.resolution;

import com.sun.tools.javac.code.Type;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.bound.Equal;
import org.checkerframework.framework.util.typeinference8.bound.Throws;
import org.checkerframework.framework.util.typeinference8.types.Dependencies;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;

public class Resolution {
    public static BoundSet resolve(List<Variable> as, BoundSet boundSet, Context context) {
        if (as.isEmpty() || boundSet.getInstantiations(as).size() == as.size()) {
            // If all variables have an instantiation, resolution is complete.
            return boundSet;
        }
        Dependencies dependencies = boundSet.getDependencies();

        List<Variable> resolvedVars = boundSet.getInstantiatedVariables();
        as.removeAll(resolvedVars);
        Queue<Variable> unresolvedVars = new LinkedList<>(as);
        Resolution resolution = new Resolution(context, dependencies, resolvedVars);
        boundSet = resolution.resolve(boundSet, unresolvedVars);

        if (unresolvedVars.isEmpty()) {
            return boundSet;
        }
        throw new RuntimeException("Not implemented.");
    }

    private final Context context;
    private final Dependencies dependencies;
    private final List<Variable> resolvedVars;

    public Resolution(Context context, Dependencies dependencies, List<Variable> resolvedVars) {
        this.context = context;
        this.dependencies = dependencies;
        this.resolvedVars = resolvedVars;
    }

    public BoundSet resolve(BoundSet boundSet, Queue<Variable> unresolvedVars) {
        Queue<Variable> queue = new LinkedList<>(unresolvedVars);
        unresolvedVars.clear();
        // This loop is looking to resolve the variable with the smallest set of dependencies that
        // have not been resolved.
        while (!unresolvedVars.isEmpty()) {
            LinkedHashSet<Variable> smallestDependencySet = null;
            queue.addAll(unresolvedVars);
            unresolvedVars.clear();
            while (!queue.isEmpty()) {
                Variable alpha = queue.remove();
                LinkedHashSet<Variable> alphasDependencySet = dependencies.get(alpha);
                alphasDependencySet.removeAll(resolvedVars);

                if (smallestDependencySet == null
                        || alphasDependencySet.size() < smallestDependencySet.size()) {
                    smallestDependencySet = alphasDependencySet;
                }

                if (smallestDependencySet.size() == 1) {
                    // If the size is 1, then alpha has the smallest possible set of unresolved dependencies.
                    // (A variable is always dependent on itself.) So, stop looking for smaller ones.
                    unresolvedVars.addAll(queue);
                    break;
                } else {
                    unresolvedVars.add(alpha);
                }
            }

            // Resolve the smallest unresolved dependency set.
            boundSet = resolve(smallestDependencySet, boundSet);
            resolvedVars.addAll(smallestDependencySet);
        }
        assert unresolvedVars.isEmpty();
        return boundSet;
    }

    private BoundSet resolve(LinkedHashSet<Variable> as, BoundSet boundSet) {
        BoundSet resolvedBounds;
        if (boundSet.containsCapture(as)) {
            resolvedBounds = resolve2(as, boundSet, context);
        } else {
            BoundSet copy = new BoundSet(boundSet);
            resolvedBounds = resolve1(as, boundSet);
            if (resolvedBounds.containsFalse()) {
                boundSet = copy;
                resolvedBounds = resolve2(as, boundSet, context);
            }
        }
        return resolvedBounds;
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.4-320-A */
    private BoundSet resolve1(LinkedHashSet<Variable> as, BoundSet boundSet) {
        BoundSet resolvedBoundSet = new BoundSet(context);
        for (Variable ai : as) {
            assert !boundSet.hasInstantiation(ai);
            LinkedHashSet<ProperType> lowerBounds = boundSet.findProperLowerBounds(ai);
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
                resolvedBoundSet.add(Equal.create(ai, new ProperType(ti, context)));
                continue;
            }

            LinkedHashSet<ProperType> upperBounds = boundSet.findProperUpperBounds(ai);
            if (!upperBounds.isEmpty()) {
                TypeMirror ti = null;
                for (ProperType liProperType : upperBounds) {
                    TypeMirror li = liProperType.getProperType();
                    if (ti == null) {
                        ti = li;
                    } else {
                        ti = InternalInferenceUtils.glb(context.env, ti, li);
                    }
                }
                List<Throws> throwsBounds = boundSet.findThrowsBounds(ai);
                if (!throwsBounds.isEmpty()) {
                    // TODO: if ti is Exception or Throwable ti = RuntimeException
                    throw new RuntimeException("Not implemented.");
                }
                resolvedBoundSet.add(Equal.create(ai, new ProperType(ti, context)));
                continue;
            }
            resolvedBoundSet = BoundSet.FALSE;
            break;
        }
        boundSet.incorporateToFixedPoint(resolvedBoundSet);

        return boundSet;
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.4-320-B */
    private static BoundSet resolve2(
            LinkedHashSet<Variable> as, BoundSet boundSet, Context context) {
        BoundSet resolvedBoundSet = new BoundSet(context);
        for (Variable ai : as) {
            assert !boundSet.hasInstantiation(ai);
            LinkedHashSet<ProperType> lowerBounds = boundSet.findProperLowerBounds(ai);
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

            LinkedHashSet<ProperType> upperBounds = boundSet.findProperUpperBounds(ai);
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
            WildcardType wildcardType;
            try {
                wildcardType = context.env.getTypeUtils().getWildcardType(lowerBound, upperBound);
            } catch (Exception ex) {
                return BoundSet.FALSE;
            }
            TypeMirror freshTypeVar = context.types.capture((Type) wildcardType);
            resolvedBoundSet.add(Equal.create(ai, new ProperType(freshTypeVar, context)));
        }
        boundSet.removeCaptures(as);
        boundSet.incorporateToFixedPoint(resolvedBoundSet);
        return boundSet;
    }
}
