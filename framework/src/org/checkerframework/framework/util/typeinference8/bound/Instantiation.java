package org.checkerframework.framework.util.typeinference8.bound;

import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Variable;

public class Instantiation extends Bound {
    private final Variable a;
    private final ProperType t;

    public Instantiation(Variable var, ProperType properType) {
        super();
        this.a = var;
        this.t = properType;
    }

    @Override
    public Kind getKind() {
        return null;
    }

    public Variable getA() {
        return a;
    }

    public ProperType getT() {
        return t;
    }

    @Override
    public String toString() {
        return a.toString();
    }
}
