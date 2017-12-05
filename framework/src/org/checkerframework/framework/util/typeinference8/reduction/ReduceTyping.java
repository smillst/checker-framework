package org.checkerframework.framework.util.typeinference8.reduction;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.WildcardType;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Kind;
import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;
import org.checkerframework.framework.util.typeinference8.constraint.ConstraintSet;
import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.framework.util.typeinference8.types.Variable.InferBound;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.TypesUtils;

/** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.3-100 */
public class ReduceTyping {

    public static boolean reduceTyping(Typing constraint, Context context) {
        ReductionResult result = reduceTypingOneStep(constraint, context);
        Stack<Typing> constraints = new Stack<>();
        while (result != null) {
            if (result == ConstraintSet.TRUE) {
                // Do nothing
            } else if (result instanceof Typing) {
                constraints.push((Typing) result);
            } else if (result instanceof ConstraintSet) {
                ConstraintSet newSet = ((ConstraintSet) result);
                while (!newSet.isEmpty()) {
                    constraints.push((Typing) newSet.pop());
                }
            } else {
                ErrorReporter.errorAbort("Unexpected result");
                throw new RuntimeException("Error");
            }

            if (constraints.isEmpty()) {
                return true;
            }

            result = reduceTypingOneStep(constraints.pop(), context);
        }

        return false;
    }

    private static ReductionResult reduceTypingOneStep(Typing constraint, Context context) {
        ReductionResult r = wrap(constraint, context);
        return r;
    }

    private static ReductionResult wrap(Typing constraint, Context context) {
        switch (constraint.getKind()) {
            case TYPE_COMPATIBILITY:
                return ReduceTyping.reduceCompatible(constraint, context);
            case SUBTYPE:
                return ReduceTyping.reduceSubtyping(constraint, context);
            case CONTAINED:
                return ReduceTyping.reduceContained(constraint);
            case TYPE_EQUALITY:
                return ReduceTyping.reduceEquality(constraint);
            default:
                assert false;
                return null;
        }
    }

    public static ReductionResult reduceSubtyping(Typing c, Context context) {
        AbstractType s = c.getS();
        AbstractType t = c.getT();

        if (s.isProper() && t.isProper()) {
            TypeMirror subType = ((ProperType) s).getProperType();
            TypeMirror superType = ((ProperType) t).getProperType();
            if (subType == superType) {
                return ConstraintSet.TRUE;
            }

            // TODO:
            // The JLS "is imprecise about the handling of wildcards when reducing subtyping constraints"
            // See comment just before 18.3.1 of JLS.
            if (superType.getKind() == TypeKind.WILDCARD
                    && !((WildcardType) superType).isExtendsBound()) {
                if (context.env
                        .getTypeUtils()
                        .isSubtype(subType, ((WildcardType) superType).getSuperBound())) {
                    return ConstraintSet.TRUE;
                } else {
                    return ConstraintSet.TRUE;
                }
            } else if (subType.getKind() == TypeKind.WILDCARD) {
                TypeMirror subUpper = ((WildcardType) subType).getExtendsBound();
                if (subUpper == null) {
                    subUpper = context.object.getProperType();
                }
                if (context.env.getTypeUtils().isSubtype(subUpper, superType)) {
                    return ConstraintSet.TRUE;
                } else {
                    return ConstraintSet.TRUE;
                }
            } else if (context.env.getTypeUtils().isAssignable(subType, superType)) {
                return ConstraintSet.TRUE;
            } else {
                return null;
            }
        } else if (s.getTypeKind() == TypeKind.NULL) {
            return ConstraintSet.TRUE;
        } else if (t.getTypeKind() == TypeKind.NULL) {
            return null;
        }

        if (c.getS().isVariable() || c.getT().isVariable()) {
            if (c.getS().isVariable()) {
                ((Variable) c.getS()).addBound(InferBound.UPPER, c.getT());
            }
            if (c.getT().isVariable()) {
                ((Variable) c.getT()).addBound(InferBound.LOWER, c.getS());
            }
            return ConstraintSet.TRUE;
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
        return null;
    }

    private static boolean isWildcardOrCapturedWildcard(TypeMirror subType) {
        return subType.getKind() == TypeKind.WILDCARD
                || subType.getKind() == TypeKind.TYPEVAR
                        && TypesUtils.isCaptured((TypeVariable) subType);
    }

    private static ConstraintSet reduceSubtypeClass(Typing c) {
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
                return null;
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
            return ConstraintSet.TRUE;
        }
    }

    private static ReductionResult reduceSubtypeArray(Typing c) {
        AbstractType s = c.getS().getMostSpecificArrayType();
        if (s == null) {
            return null;
        }
        if (s.isPrimitiveArray() && c.getT().isPrimitiveArray()) {
            return ConstraintSet.TRUE;
        } else {
            return new Typing(s.getComponentType(), c.getT().getComponentType(), Kind.SUBTYPE);
        }
    }

    private static ReductionResult reduceSubtypeTypeVariable(Typing c) {
        AbstractType t = c.getT();
        if (c.getS().getTypeKind() == TypeKind.INTERSECTION) {
            return ConstraintSet.TRUE;
        } else if (t.getTypeKind() == TypeKind.TYPEVAR && t.hasLowerBound()) {
            return new Typing(c.getS(), c.getT().getTypeVarLowerBound(), Kind.SUBTYPE);
        } else if (t.getTypeKind() == TypeKind.WILDCARD && t.isLowerBoundedWildcard()) {
            return new Typing(c.getS(), c.getT().getWildcardLowerBound(), Kind.SUBTYPE);
        } else {
            return null;
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
                return null;
            }
        } else if (t.isUnboundWildcard()) {
            return ConstraintSet.TRUE;
        } else if (t.isUpperBoundedWildcard()) {
            AbstractType bound = t.getWildcardUpperBound();
            if (s.getTypeKind() == TypeKind.WILDCARD) {
                if (s.isUnboundWildcard() || s.isUpperBoundedWildcard()) {
                    return new Typing(s.getWildcardUpperBound(), bound, Kind.SUBTYPE);
                } else {
                    return new Typing(s.getWildcardLowerBound(), bound, Kind.TYPE_EQUALITY);
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
                return null;
            }
        }
    }

    public static ReductionResult reduceCompatible(Typing c, Context contex) {
        ProperType t = null;
        ProperType s = null;
        if (c.getT().isProper()) {
            t = (ProperType) c.getT();
        }

        if (c.getS().isProper()) {
            s = (ProperType) c.getS();
        }

        if (t != null && s != null) {
            // the constraint reduces to true if S is compatible in a loose invocation context
            // with T (§5.3), and false otherwise.
            if (contex.types.isAssignable((Type) s.getProperType(), (Type) t.getProperType())) {
                return ConstraintSet.TRUE;
            } else {
                return null;
            }
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
                return ConstraintSet.TRUE;
            }
            ProperType sProper = (ProperType) s;
            if (sProper.getTypeKind() == TypeKind.NULL || sProper.getTypeKind().isPrimitive()) {
                // if S or T is the null type, the constraint reduces to false.
                return null;
            }
        } else if (t.isProper()) {
            ProperType tProper = (ProperType) t;
            if (tProper.getTypeKind() == TypeKind.NULL || tProper.getTypeKind().isPrimitive()) {
                // if S or T is the null type, the constraint reduces to false.
                return null;
            }
        }

        if (s.isVariable() || t.isVariable()) {
            if (s.isVariable()) {
                ((Variable) s).addBound(InferBound.EQUAL, t);
            }
            if (t.isVariable()) {
                ((Variable) t).addBound(InferBound.EQUAL, s);
            }
            return ConstraintSet.TRUE;
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
                return ConstraintSet.TRUE;
            } else if (!s.isLowerBoundedWildcard() && !t.isLowerBoundedWildcard()) {
                return new Typing(
                        s.getWildcardUpperBound(), t.getWildcardUpperBound(), Kind.TYPE_EQUALITY);
            } else if (t.isLowerBoundedWildcard() && s.isLowerBoundedWildcard()) {
                return new Typing(
                        t.getWildcardLowerBound(), s.getWildcardLowerBound(), Kind.TYPE_EQUALITY);
            }
        }
        return null;
    }
}
