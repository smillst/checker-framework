package org.checkerframework.framework.util.typeinference8.types;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.util.Context;

public class Variable extends AbstractType {
    final TypeVariable p;

    public Variable(TypeVariable p) {
        this.p = p;
    }

    @Override
    public AbstractType asSuper(TypeMirror first, Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Kind getKind() {
        return Kind.VARIABLE;
    }

    @Override
    public boolean isParameterizedType() {
        return false;
    }

    @Override
    public AbstractType getMostSpecificArrayType(Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPrimitiveArray() {
        return false;
    }

    @Override
    public boolean isUpperBoundedWildcard() {
        return false;
    }

    @Override
    public List<AbstractType> getIntersectionBounds() {
        return null;
    }

    @Override
    public AbstractType getTypeVarLowerBound() {
        return null;
    }

    @Override
    public boolean hasLowerBound() {
        return false;
    }

    @Override
    public Collection<? extends Variable> getInferenceVariables() {
        return Collections.singleton(this);
    }

    @Override
    public AbstractType getNonWildcardParameterization() {
        return null;
    }

    @Override
    public boolean isWildcardParameterizedType() {
        return false;
    }

    @Override
    public AbstractType applyInstantiations(List<Instantiation> instantiations, Context context) {
        return this;
    }

    @Override
    public List<AbstractType> getFunctionTypeParameters() {
        return null;
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

    @Override
    public boolean isUnboundWildcard() {
        return false;
    }

    @Override
    public boolean isLowerBoundedWildcard() {
        return false;
    }
}
