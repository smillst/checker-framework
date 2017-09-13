package org.checkerframework.framework.util.typeinference8.bound;

import java.util.Map.Entry;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceTypeUtil;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.javacutil.ErrorReporter;

public class BoundUtil {

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
     * <p>If Pl has no TypeBound, the bound {@literal al <:Object} appears in the set.
     *
     * <p>Otherwise, for each type T delimited by & in the TypeBound, the bound {@literal al <:
     * T[P1:=a1,..., Pp:=ap]} appears in the set; if this results in no proper upper bounds for al
     * (only dependencies), then the bound {@literal al <: Object} also appears in the set.
     *
     * @param object type mirror for java.lang.Object
     */
    public static BoundSet initialBounds(Theta map, TypeMirror object) {
        BoundSet boundSet = new BoundSet();

        for (Entry<TypeVariable, Variable> entry : map.entrySet()) {
            TypeVariable pl = entry.getKey();
            Variable al = entry.getValue();
            TypeMirror upperBound = pl.getUpperBound();
            BoundSet boundsForAL = initialBoundForL(map, al, upperBound);
            if (!boundsForAL.containsProperUpperBound(al)) {
                boundsForAL.add(Subtype.createSubtype(al, new ProperType(object)));
            }
            boundSet.add(boundsForAL);
        }
        return boundSet;
    }

    /**
     * If Pl has no TypeBound, the bound {@literal al <: Object} appears in the set. Otherwise, for
     * each type T delimited by & in the TypeBound, the bound {@literal al <: T[P1:=a1,..., Pp:=ap]}
     * appears in the set; if this results in no proper upper bounds for al (only dependencies),
     * then the bound {@literal al <: Object} also appears in the set.
     */
    private static BoundSet initialBoundForL(Theta map, Variable al, TypeMirror upperBound) {
        BoundSet boundSet = new BoundSet();
        switch (upperBound.getKind()) {
            case DECLARED:
            case TYPEVAR:
                AbstractType t1 = InferenceTypeUtil.create(upperBound, map);
                boundSet.add(Subtype.createSubtype(al, t1));
                break;
            case INTERSECTION:
                for (TypeMirror bound : ((IntersectionType) upperBound).getBounds()) {
                    boundSet.add(initialBoundForL(map, al, bound));
                }
                break;
            default:
                ErrorReporter.errorAbort("Unexpected kind: %s", upperBound.getKind());
        }
        return boundSet;
    }
}
