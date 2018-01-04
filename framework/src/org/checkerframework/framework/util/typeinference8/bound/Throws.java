package org.checkerframework.framework.util.typeinference8.bound;

import org.checkerframework.framework.util.typeinference8.types.AbstractType;

public class Throws {
    private final AbstractType alpha;

    public Throws(AbstractType thrownType) {
        super();
        this.alpha = thrownType;
    }

    public AbstractType getAlpha() {
        return alpha;
    }
}
