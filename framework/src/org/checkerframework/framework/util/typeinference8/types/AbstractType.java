package org.checkerframework.framework.util.typeinference8.types;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;

public abstract class AbstractType {
    /**
     * Returns null if superType isn't a super type.
     *
     * @param superType
     * @return
     */
    public abstract AbstractType asSuper(TypeMirror superType);

    public abstract boolean isObject();

    public abstract AbstractType getFunctionTypeReturn();

    public abstract boolean isRaw();

    public abstract AbstractType replaceTypeArgs(List<AbstractType> ts);

    public enum Kind {
        PROPER,
        VARIABLE,
        INFERENCE_TYPE
    }

    public abstract Kind getKind();

    public final boolean isProper() {
        return getKind() == Kind.PROPER;
    }

    public final boolean isVariable() {
        return getKind() == Kind.VARIABLE;
    }

    public boolean isInferenceType() {
        return getKind() == Kind.INFERENCE_TYPE;
    }

    public abstract boolean isParameterizedType();

    public abstract AbstractType getMostSpecificArrayType();

    public abstract boolean isPrimitiveArray();

    public abstract boolean isUpperBoundedWildcard();

    public abstract List<AbstractType> getIntersectionBounds();

    public abstract AbstractType getTypeVarLowerBound();

    public abstract boolean hasLowerBound();

    public abstract Collection<Variable> getInferenceVariables();

    public abstract Iterator<ProperType> getTypeParameterBounds();

    public abstract boolean isWildcardParameterizedType();

    public abstract AbstractType applyInstantiations(List<Instantiation> instantiations);

    public abstract List<AbstractType> getFunctionTypeParameters();

    public abstract TypeKind getTypeKind();

    public abstract List<AbstractType> getTypeArguments();

    public abstract AbstractType getComponentType();

    public abstract AbstractType getWildcardUpperBound();

    public abstract AbstractType getWildcardLowerBound();

    public abstract boolean isUnboundWildcard();

    public abstract boolean isLowerBoundedWildcard();
}
