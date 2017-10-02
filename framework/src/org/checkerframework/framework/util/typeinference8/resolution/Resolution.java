package org.checkerframework.framework.util.typeinference8.resolution;

import java.util.LinkedHashSet;
import java.util.List;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.bound.Equal;
import org.checkerframework.framework.util.typeinference8.bound.Throws;
import org.checkerframework.framework.util.typeinference8.types.Dependencies;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InternalUtils;

public class Resolution {
    public static BoundSet resolve(List<Variable> as, BoundSet boundSet, Context context) {
        if (as.isEmpty()) {
            return BoundSet.TRUE;
        }
        Resolution resolution = new Resolution(context);
        Dependencies dependencies = boundSet.getDependencies();
        BoundSet resolved = new BoundSet(context);
        if (boundSet.getInstantiations(as).size() == as.size()) {
            // If all variables have an instantiation, resolution is complete.
            return boundSet;
        }
        for (Variable alpha : as) {
            resolved.add(resolution.resolve(dependencies.get(alpha), boundSet));
        }

        return resolved;
    }

    private final Context context;

    public Resolution(Context context) {
        this.context = context;
    }

    private BoundSet resolve(LinkedHashSet<Variable> as, BoundSet boundSet) {
        BoundSet resolvedBounds;
        if (boundSet.containsCapture(as)) {
            resolvedBounds = resolve2(as, boundSet);
        } else {
            resolvedBounds = resolve1(as, boundSet);
            if (resolvedBounds.containsFalse()) {
                //TODO: must boundSet is sideeffected above, need a way to roll back.
                resolvedBounds = resolve2(as, boundSet);
            }
        }
        return resolvedBounds;
    }

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.4-320-A */
    private BoundSet resolve1(LinkedHashSet<Variable> as, BoundSet boundSet) {
        BoundSet resolvedBoundSet = new BoundSet(context);
        for (Variable ai : as) {
            if (boundSet.hasInstantiation(ai)) {
                continue;
            }
            LinkedHashSet<ProperType> lowerBounds = boundSet.findProperLowerBounds(ai);
            if (!lowerBounds.isEmpty()) {
                TypeMirror ti = null;
                for (ProperType liProperType : lowerBounds) {
                    TypeMirror li = liProperType.getProperType();
                    if (ti == null) {
                        ti = li;
                    } else {
                        ti = InternalUtils.lub(context.env, ti, li);
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
                        ti = InternalUtils.glb(context.env, ti, li);
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
    private static BoundSet resolve2(LinkedHashSet<Variable> as, BoundSet boundSet) {
        throw new RuntimeException("Resolve2: Not Implemented");
    }
}
