package org.checkerframework.framework.util.typeinference8.bound;

public class Throws extends Bound {
    @Override
    public Kind getKind() {
        return Kind.THROWS;
    }
}
