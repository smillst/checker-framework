package org.checkerframework.framework.util.typeinference8.types;

import java.util.Collection;
import java.util.List;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.util.Context;

public abstract class AbstractType {
    /**
     * Returns null if superType isn't a super type.
     *
     * @param superType
     * @param context
     * @return
     */
    public abstract AbstractType asSuper(TypeMirror superType, Context context);

    public abstract boolean isObject();

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

    public abstract AbstractType getMostSpecificArrayType(Context context);

    public abstract boolean isPrimitiveArray();

    public abstract boolean isUpperBoundedWildcard();

    public abstract List<AbstractType> getIntersectionBounds();

    public abstract AbstractType getTypeVarLowerBound();

    public abstract boolean hasLowerBound();

    public abstract Collection<? extends Variable> getInferenceVariables();

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.9-200-C */
    public abstract AbstractType getNonWildcardParameterization();

    public abstract boolean isWildcardParameterizedType();

    public abstract AbstractType applyInstantiations(
            List<Instantiation> instantiations, Context context);

    public abstract List<AbstractType> getFunctionTypeParameters();

    public abstract TypeKind getTypeKind();

    public abstract List<AbstractType> getTypeArguments();

    public abstract AbstractType getComponentType();

    public abstract AbstractType getWildcardUpperBound(Context context);

    public abstract AbstractType getWildcardLowerBound();

    public abstract boolean isUnboundWildcard();

    public abstract boolean isLowerBoundedWildcard();
}
