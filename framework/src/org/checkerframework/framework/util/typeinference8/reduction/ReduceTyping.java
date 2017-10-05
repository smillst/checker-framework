package org.checkerframework.framework.util.typeinference8.reduction;

import com.sun.tools.javac.code.Type;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.typeinference8.bound.BoundSet;
import org.checkerframework.framework.util.typeinference8.bound.Equal;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Kind;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.util.Context;

/** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.3-100 */
public class ReduceTyping {

    public static ReductionResult reduceSubtyping(Typing c, Context context) {
        AbstractType s = c.getS();
        AbstractType t = c.getT();

        if (s.isProper() && t.isProper()) {
            TypeMirror subType = ((ProperType) s).getProperType();
            TypeMirror superType = ((ProperType) t).getProperType();
            if (context.types.isSubtype((Type) subType, (Type) superType)) {
                return BoundSet.TRUE;
            } else {
                return BoundSet.FALSE;
            }
        } else if (s.getTypeKind() == TypeKind.NULL) {
            return BoundSet.TRUE;
        } else if (t.getTypeKind() == TypeKind.NULL) {
            return BoundSet.FALSE;
        }

        if (c.getS().isVariable() || c.getT().isVariable()) {
            return org.checkerframework.framework.util.typeinference8.bound.Subtype.createSubtype(
                    c.getS(), c.getT());
        }

        switch (c.getT().getTypeKind()) {
            case DECLARED:
                return reduceSubtypeClass(c);
            case ARRAY:
                return reduceSubtypeArray(c);
            case TYPEVAR:
            case WILDCARD: // ?
                return reduceSubtypeTypeVariable(c);
            case INTERSECTION:
                return reduceSubtypingIntersection(c);
        }
        return BoundSet.FALSE;
    }

    private static ReductionResult reduceSubtypeClass(Typing c) {
        if (c.getT().isParameterizedType()) {
            // let A1, ..., An be the type arguments of T. Among the supertypes of S, a
            // corresponding class or interface type is identified, with type arguments B1, ...,
            // Bn. If no such type exists, the constraint reduces to false. Otherwise, the
            // constraint reduces to the following new constraints:
            // for all i (1 <= i <= n), ‹Bi <= Ai›.

            AbstractType t = c.getT();
            AbstractType s = c.getS();

            TypeMirror tTypeMirror =
                    t.isProper() ? ((ProperType) t).getProperType() : ((InferenceType) t).getType();
            AbstractType sAsSuper = s.asSuper(tTypeMirror);
            if (sAsSuper == null) {
                return BoundSet.FALSE;
            }

            List<AbstractType> Bs = sAsSuper.getTypeArguments();
            Iterator<AbstractType> As = t.getTypeArguments().iterator();
            ConstraintSet set = new ConstraintSet();
            for (AbstractType b : Bs) {
                AbstractType a = As.next();
                set.add(new Typing(b, a, Kind.CONTAINED));
            }

            return set;
        } else {
            //the constraint reduces to true if T is among the supertypes of S, and false otherwise.
            return BoundSet.TRUE;
        }
    }

    private static ReductionResult reduceSubtypeArray(Typing c) {
        AbstractType s = c.getS().getMostSpecificArrayType();
        if (s == null) {
            return BoundSet.FALSE;
        }
        if (s.isPrimitiveArray() && c.getT().isPrimitiveArray()) {
            return BoundSet.TRUE;
        } else {
            return new Typing(s.getComponentType(), c.getT().getComponentType(), Kind.SUBTYPE);
        }
    }

    private static ReductionResult reduceSubtypeTypeVariable(Typing c) {
        if (c.getS().getTypeKind() == TypeKind.INTERSECTION) {
            return BoundSet.TRUE;
        } else if (c.getT().hasLowerBound()) {
            return new Typing(c.getS(), c.getT().getTypeVarLowerBound(), Kind.SUBTYPE);
        } else {
            return BoundSet.FALSE;
        }
    }

    private static ReductionResult reduceSubtypingIntersection(Typing c) {
        ConstraintSet constraintSet = new ConstraintSet();
        for (AbstractType bound : c.getT().getIntersectionBounds()) {
            constraintSet.add(new Typing(c.getS(), bound, Kind.SUBTYPE));
        }
        return constraintSet;
    }

    public static ReductionResult reduceContained(Typing contained) {
        AbstractType s = contained.getS();
        AbstractType t = contained.getT();
        if (t.getTypeKind() != TypeKind.WILDCARD) {
            if (s.getTypeKind() != TypeKind.WILDCARD) {
                return new Typing(s, t, Kind.TYPE_EQUALITY);
            } else {
                return BoundSet.FALSE;
            }
        } else if (t.isUnboundWildcard()) {
            return BoundSet.TRUE;
        } else if (t.isUpperBoundedWildcard()) {
            AbstractType bound = t.getWildcardUpperBound();
            if (s.getTypeKind() == TypeKind.WILDCARD) {
                if (s.isUnboundWildcard() || s.isUpperBoundedWildcard()) {
                    return new Typing(s.getWildcardUpperBound(), bound, Kind.SUBTYPE);
                } else {
                    return new Typing(s.getWildcardUpperBound(), bound, Kind.TYPE_EQUALITY);
                }
            } else {
                return new Typing(s, bound, Kind.SUBTYPE);
            }
        } else {
            AbstractType tPrime = t.getWildcardLowerBound();
            if (s.getTypeKind() != TypeKind.WILDCARD) {
                return new Typing(tPrime, s, Kind.SUBTYPE);
            } else if (s.isLowerBoundedWildcard()) {
                return new Typing(tPrime, s.getWildcardLowerBound(), Kind.SUBTYPE);
            } else {
                return BoundSet.FALSE;
            }
        }
    }

    public static ReductionResult reduceCompatible(Typing c) {
        ProperType t = null;
        ProperType s = null;
        if (c.getT().isProper()) {
            t = (ProperType) c.getT();
        }

        if (c.getT().isProper()) {
            s = (ProperType) c.getT();
        }

        if (t != null && s != null) {
            // the constraint reduces to true if S is compatible in a loose invocation context
            // with T (§5.3), and false otherwise.
            return BoundSet.TRUE;
        } else if (s != null && s.getTypeKind().isPrimitive()) {
            return new Typing(s.boxType(), c.getT(), Kind.TYPE_COMPATIBILITY);
        } else if (t != null && t.getTypeKind().isPrimitive()) {
            return new Typing(c.getS(), t.boxType(), Kind.TYPE_EQUALITY);

        } else {
            // TODO: handle unchecked conversions
            // See points 4 & 5 in https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.2
            return new Typing(c.getS(), c.getT(), Kind.SUBTYPE);
        }
    }

    public static ReductionResult reduceEquality(Typing constraint) {
        AbstractType s = constraint.getS();
        AbstractType t = constraint.getT();

        if (s.isProper()) {
            if (t.isProper()) {
                // If S and T are proper types, the constraint reduces to true if S is the same
                // as T (4.3.4), and false otherwise.
                return BoundSet.TRUE;
            }
            ProperType sProper = (ProperType) s;
            if (sProper.getTypeKind() == TypeKind.NULL || sProper.getTypeKind().isPrimitive()) {
                // if S or T is the null type, the constraint reduces to false.
                return BoundSet.FALSE;
            }
        } else if (t.isProper()) {
            ProperType tProper = (ProperType) t;
            if (tProper.getTypeKind() == TypeKind.NULL || tProper.getTypeKind().isPrimitive()) {
                // if S or T is the null type, the constraint reduces to false.
                return BoundSet.FALSE;
            }
        }

        if (s.isVariable()) {
            return Equal.create(s, t);
        } else if (t.isVariable()) {
            return Equal.create(s, t);
        }

        List<AbstractType> sTypeArgs = s.getTypeArguments();
        List<AbstractType> tTypeArgs = t.getTypeArguments();
        if (sTypeArgs != null && tTypeArgs != null && sTypeArgs.size() == tTypeArgs.size()) {
            // Assume if both have type arguments, then s and t are class or interface types with
            // the same erasure
            ConstraintSet constraintSet = new ConstraintSet();
            for (int i = 0; i < tTypeArgs.size(); i++) {
                constraintSet.add(
                        new Typing(tTypeArgs.get(i), sTypeArgs.get(i), Kind.TYPE_EQUALITY));
            }
            return constraintSet;
        }

        AbstractType sComponentType = s.getComponentType();
        AbstractType tComponentType = t.getComponentType();
        if (sComponentType != null && tComponentType != null) {
            return new Typing(sComponentType, tComponentType, Kind.TYPE_EQUALITY);
        }

        if (t.getTypeKind() == TypeKind.WILDCARD && s.getTypeKind() == TypeKind.WILDCARD) {
            if (t.isUnboundWildcard() && s.isUnboundWildcard()) {
                return BoundSet.TRUE;
            } else if (!s.isLowerBoundedWildcard() && !t.isLowerBoundedWildcard()) {
                return new Typing(
                        s.getWildcardUpperBound(), t.getWildcardUpperBound(), Kind.TYPE_EQUALITY);
            } else if (t.isLowerBoundedWildcard() && s.isLowerBoundedWildcard()) {
                return new Typing(
                        t.getWildcardLowerBound(), s.getWildcardLowerBound(), Kind.TYPE_EQUALITY);
            }
        }
        return BoundSet.FALSE;
    }
}
