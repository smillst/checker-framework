package org.checkerframework.framework.util.typeinference8.resolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.ContainsInferenceVariable;
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
        if (a.hasInstantiation()) {
            return boundSet;
        }
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
            // First resolve the non-capture variables using the usual resolution algorithm.
            for (Variable ai : as) {
                if (!ai.isCaptureVariable()) {
                    resolve1(ai);
                }
            }
            as.removeAll(boundSet.getInstantiatedVariables());
            // Then resolve the capture variables
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
            resolve1(ai);
            if (!ai.hasInstantiation()) {
                resolvedBoundSet.addFalse();
                break;
            }
        }
        boundSet.incorporateToFixedPoint(resolvedBoundSet);
        return boundSet;
    }

    private void resolve1(Variable ai) {
        assert !ai.hasInstantiation();
        LinkedHashSet<ProperType> lowerBounds = ai.findProperLowerBounds();
        if (!lowerBounds.isEmpty()) {
            TypeMirror ti = null;
            for (ProperType liProperType : lowerBounds) {
                TypeMirror li = liProperType.getJavaType();
                if (ti == null) {
                    ti = li;
                } else {
                    ti = InternalInferenceUtils.lub(context.env, ti, li);
                }
            }
            ai.addBound(InferBound.EQUAL, new ProperType(ti, context));
            return;
        }

        LinkedHashSet<ProperType> upperBounds = ai.findProperUpperBounds();
        if (!upperBounds.isEmpty()) {
            TypeMirror ti = null;
            boolean useRuntimeEx = false;
            for (ProperType liProperType : upperBounds) {
                TypeMirror li = liProperType.getJavaType();
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
        }
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.4-320-B */
    private static BoundSet resolve2(
            LinkedHashSet<Variable> as, BoundSet boundSet, Context context) {
        assert !boundSet.containsFalse();
        boundSet.removeCaptures(as);
        BoundSet resolvedBoundSet = new BoundSet(context);
        List<Variable> asList = new ArrayList<>();
        List<TypeVariable> typeVar = new ArrayList<>();
        List<TypeMirror> typeArg = new ArrayList<>();

        for (Variable ai : as) {
            ai.applyInstantiationsToBounds(boundSet.getInstantiationsAll());
            if (ai.hasInstantiation()) {
                // If ai is equal to a variable that was resolved previously,
                // ai would now have an instantiation.
                continue;
            }
            asList.add(ai);
            LinkedHashSet<ProperType> lowerBounds = ai.findProperLowerBounds();
            TypeMirror lowerBound = null;
            for (ProperType liProperType : lowerBounds) {
                TypeMirror li = liProperType.getJavaType();
                if (lowerBound == null) {
                    lowerBound = li;
                } else {
                    lowerBound = InternalInferenceUtils.lub(context.env, lowerBound, li);
                }
            }

            LinkedHashSet<AbstractType> upperBounds = ai.upperBounds();
            TypeMirror upperBound = null;
            for (AbstractType liAb : upperBounds) {
                TypeMirror li = liAb.getJavaType();
                if (upperBound == null) {
                    upperBound = li;
                } else {
                    upperBound = InternalInferenceUtils.glb(context.env, upperBound, li);
                }
            }

            typeVar.add(ai.getJavaType());
            // TODO: This won't square with the capture that javac produces.
            TypeMirror freshTypeVar =
                    InternalInferenceUtils.getFreshTypeVar(context, lowerBound, upperBound);
            typeArg.add(freshTypeVar);
        }

        // Recursive types:
        for (int i = 0; i < typeArg.size(); i++) {
            Variable ai = asList.get(i);
            TypeMirror inst = typeArg.get(i);
            TypeVariable typeVariableI = ai.getJavaType();
            if (ContainsInferenceVariable.hasAnyInferenceVar(
                    Collections.singleton(typeVariableI), inst)) {
                // If the instantiation of ai includes a reference to ai,
                // then substitute ai with an unbound wildcard.
                TypeMirror unbound = context.env.getTypeUtils().getWildcardType(null, null);
                inst =
                        InternalInferenceUtils.subs(
                                context.env,
                                inst,
                                Collections.singletonList(typeVariableI),
                                Collections.singletonList(unbound));
                typeArg.remove(i);
                typeArg.add(i, inst);
            }
        }
        List<TypeMirror> subsTypeArg = new ArrayList<>();
        for (TypeMirror type : typeArg) {
            subsTypeArg.add(InternalInferenceUtils.subs(context.env, type, typeVar, typeArg));
        }
        for (int i = 0; i < asList.size(); i++) {
            Variable ai = asList.get(i);
            ContainsInferenceVariable.getInferenceVar(
                    Collections.singleton(ai.getJavaType()), subsTypeArg.get(i));
            ai.addBound(InferBound.EQUAL, new ProperType(subsTypeArg.get(i), context).capture());
        }

        boundSet.incorporateToFixedPoint(resolvedBoundSet);
        return boundSet;
    }
}
