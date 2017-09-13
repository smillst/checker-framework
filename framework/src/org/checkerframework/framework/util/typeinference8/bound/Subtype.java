package org.checkerframework.framework.util.typeinference8.bound;

import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;

/** {@literal S <: T } */
public abstract class Subtype extends Bound {
    public static Subtype createSubtype(AbstractType subType, AbstractType superType) {
        if (subType.getKind() == AbstractType.Kind.VARIABLE
                && superType.getKind() == AbstractType.Kind.VARIABLE) {
            return new SubtypeVV((Variable) subType, (Variable) superType);
        } else if (subType.getKind() == AbstractType.Kind.VARIABLE
                && superType.getKind() == AbstractType.Kind.PROPER) {
            return new ProperUpperBound((Variable) subType, (ProperType) superType);
        } else if (superType.getKind() == AbstractType.Kind.VARIABLE
                && subType.getKind() == AbstractType.Kind.PROPER) {
            return new ProperLowerBound((ProperType) subType, (Variable) superType);
        } else if (subType.getKind() == AbstractType.Kind.VARIABLE
                && superType.getKind() == AbstractType.Kind.INFERENCE_TYPE) {
            return new NonProperUpperBound((Variable) subType, (InferenceType) superType);
        } else if (superType.getKind() == AbstractType.Kind.VARIABLE
                && subType.getKind() == AbstractType.Kind.INFERENCE_TYPE) {
            return new NonProperLowerBound((InferenceType) subType, (Variable) superType);
        }
        throw new RuntimeException("");
    }

    public abstract AbstractType getSubtype();

    public abstract AbstractType getSupertype();

    @Override
    public Kind getKind() {
        return Kind.SUBTYPE;
    }

    /** {@literal a <: T} where a is an inference variable and T is a proper type. */
    public static class ProperUpperBound extends Subtype {
        private final Variable a;
        private final ProperType t;

        ProperUpperBound(Variable a, ProperType t) {
            this.a = a;
            this.t = t;
        }

        @Override
        public Variable getSubtype() {
            return a;
        }

        @Override
        public ProperType getSupertype() {
            return t;
        }
    }

    /** {@literal T <: a} where a is an inference variable and T is a proper type. */
    public static class ProperLowerBound extends Subtype {
        private final Variable a;
        private final ProperType t;

        ProperLowerBound(ProperType t, Variable a) {
            this.a = a;
            this.t = t;
        }

        @Override
        public ProperType getSubtype() {
            return t;
        }

        @Override
        public Variable getSupertype() {
            return a;
        }
    }

    /** {@literal a <: b} where both a and b are an inference variables. */
    public static class SubtypeVV extends Subtype {
        final Variable subtype;
        final Variable supertype;

        public SubtypeVV(Variable subtype, Variable supertype) {
            this.subtype = subtype;
            this.supertype = supertype;
        }

        @Override
        public Variable getSubtype() {
            return subtype;
        }

        @Override
        public Variable getSupertype() {
            return supertype;
        }
    }

    /** {@literal a <: T} where a is an inference variable and T is an inference type. */
    public static class NonProperUpperBound extends Subtype {
        final Variable subtype;
        final InferenceType supertype;

        public NonProperUpperBound(Variable subtype, InferenceType supertype) {
            this.subtype = subtype;
            this.supertype = supertype;
        }

        @Override
        public Variable getSubtype() {
            return subtype;
        }

        @Override
        public InferenceType getSupertype() {
            return supertype;
        }
    }
    /** {@literal T <: a} where a is an inference variable and T is an inference type. */
    public static class NonProperLowerBound extends Subtype {
        final InferenceType subtype;
        final Variable supertype;

        public NonProperLowerBound(InferenceType subtype, Variable supertype) {
            this.subtype = subtype;
            this.supertype = supertype;
        }

        @Override
        public InferenceType getSubtype() {
            return subtype;
        }

        @Override
        public Variable getSupertype() {
            return supertype;
        }
    }
}
