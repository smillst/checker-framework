package org.checkerframework.framework.util.typeinference8.types;

import java.util.List;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVariable;

public class Variable extends AbstractType {
    final TypeVariable p;

    public Variable(TypeVariable p) {
        this.p = p;
    }

    @Override
    public Kind getKind() {
        return Kind.VARIABLE;
    }

    @Override
    public TypeKind getTypeKind() {
        return TypeKind.TYPEVAR;
    }

    @Override
    public List<AbstractType> getTypeArguments() {
        return null;
    }

    @Override
    public AbstractType getComponentType() {
        return null;
    }

    @Override
    public AbstractType getWildcardUpperBound() {
        return null;
    }

    @Override
    public AbstractType getWildcardLowerBound() {
        return null;
    }
}
