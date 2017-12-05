package org.checkerframework.framework.util.typeinference8.util;

import org.checkerframework.framework.util.typeinference8.constraint.Constraint.Typing;

public class FalseBoundException extends RuntimeException {
    static final long serialVersionUID = 1;

    public FalseBoundException(Typing constraint) {
        super("Constraint: " + constraint);
    }
}
