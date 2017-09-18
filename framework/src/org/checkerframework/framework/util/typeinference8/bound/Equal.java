package org.checkerframework.framework.util.typeinference8.bound;

import org.checkerframework.framework.util.typeinference8.types.AbstractType;
import org.checkerframework.framework.util.typeinference8.types.InferenceType;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;
import org.checkerframework.javacutil.ErrorReporter;

public abstract class Equal extends Bound {
    @Override
    public Kind getKind() {
        return Kind.EQUAL;
    }

    public abstract Variable getA();

    public abstract AbstractType getT();

    /** a = T, where is a is an inference variable and T is a proper type. */
    public static class Instantiation extends Equal {
        final Variable a;
        final ProperType t;

        public Instantiation(Variable a, ProperType t) {
            this.a = a;
            this.t = t;
        }

        @Override
        public Variable getA() {
            return a;
        }

        @Override
        public ProperType getT() {
            return t;
        }
    }

    /** a = T, where is a is an inference variable and T is an inference type. */
    public static class EqualInferenceType extends Equal {
        final Variable a;
        final InferenceType t;

        private EqualInferenceType(Variable a, InferenceType t) {
            this.a = a;
            this.t = t;
        }

        @Override
        public Variable getA() {
            return a;
        }

        @Override
        public InferenceType getT() {
            return t;
        }
    }

    /** a = b, where both a and b are inference variables. */
    public static class EqualInferenceVars extends Equal {
        final Variable a;
        final Variable b;

        private EqualInferenceVars(Variable a, Variable b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public Variable getA() {
            return a;
        }

        @Override
        public Variable getT() {
            return b;
        }
    }

    public static Equal create(AbstractType s, AbstractType t) {
        if (s.isVariable()) {
            return create((Variable) s, t);
        } else if (t.isVariable()) {
            return create((Variable) t, s);
        } else {
            ErrorReporter.errorAbort("Bounds must contain at least one inference variable");
            throw new RuntimeException("");
        }
    }

    private static Equal create(Variable var, AbstractType t) {
        switch (t.getKind()) {
            case PROPER:
                return new Instantiation(var, (ProperType) t);
            case INFERENCE_TYPE:
                return new EqualInferenceType(var, (InferenceType) t);
            case VARIABLE:
                return new EqualInferenceVars(var, (Variable) t);
            default:
                ErrorReporter.errorAbort("");
                throw new RuntimeException();
        }
    }
}
