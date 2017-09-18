package org.checkerframework.framework.util.typeinference8.types;

import com.sun.tools.javac.code.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InternalUtils;
import org.checkerframework.javacutil.TypesUtils;

public class ProperType extends AbstractType {
    private final TypeMirror properType;

    public ProperType(TypeMirror properType) {
        this.properType = properType;
    }

    public TypeMirror getProperType() {
        return properType;
    }

    @Override
    public AbstractType asSuper(TypeMirror first, Context context) {
        return new ProperType(context.types.asSuper((Type) properType, ((Type) first).asElement()));
    }

    @Override
    public boolean isObject() {
        return TypesUtils.isObject(properType);
    }

    @Override
    public Kind getKind() {
        return Kind.PROPER;
    }

    @Override
    public boolean isParameterizedType() {
        return properType.getKind() == TypeKind.DECLARED
                && !((DeclaredType) properType).getTypeArguments().isEmpty();
    }

    @Override
    public AbstractType getMostSpecificArrayType(Context context) {
        TypeMirror mostSpecific = InternalUtils.getMostSpecificArrayType(properType, context.types);
        return new ProperType(mostSpecific);
    }

    @Override
    public boolean isPrimitiveArray() {
        return properType.getKind() == TypeKind.ARRAY
                && ((ArrayType) properType).getComponentType().getKind().isPrimitive();
    }

    @Override
    public List<AbstractType> getIntersectionBounds() {
        List<AbstractType> bounds = new ArrayList<>();
        for (TypeMirror bound : ((IntersectionType) properType).getBounds()) {
            bounds.add(new ProperType(bound));
        }
        return bounds;
    }

    @Override
    public AbstractType getTypeVarLowerBound() {
        return new ProperType(((TypeVariable) properType).getLowerBound());
    }

    @Override
    public boolean hasLowerBound() {
        return ((TypeVariable) properType).getLowerBound().getKind() != TypeKind.NULL;
    }

    @Override
    public Collection<? extends Variable> getInferenceVariables() {
        return Collections.emptyList();
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
        return properType.getKind();
    }

    public ProperType boxType(Context context) {
        if (properType.getKind().isPrimitive()) {
            return new ProperType(context.types.boxedClass((Type) properType).asType());
        }
        return this;
    }

    @Override
    public List<AbstractType> getTypeArguments() {
        if (properType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        List<AbstractType> typeArgs = new ArrayList<>();
        for (TypeMirror t : ((DeclaredType) properType).getTypeArguments()) {
            typeArgs.add(new ProperType(t));
        }
        return typeArgs;
    }

    @Override
    public AbstractType getComponentType() {
        if (properType.getKind() != TypeKind.ARRAY) {
            return null;
        }
        return new ProperType(((ArrayType) properType).getComponentType());
    }

    @Override
    public AbstractType getWildcardUpperBound() {
        if (properType.getKind() != TypeKind.WILDCARD) {
            return null;
        } else if (((Type.WildcardType) properType).isSuperBound()) {
            return new ProperType(((Type.WildcardType) properType).getLowerBound());
        } else {
            return null;
        }
    }

    @Override
    public AbstractType getWildcardLowerBound() {
        if (properType.getKind() != TypeKind.WILDCARD) {
            return null;
        } else if (((Type.WildcardType) properType).isExtendsBound()) {
            return new ProperType(((Type.WildcardType) properType).getUpperBound());
        } else {
            return null;
        }
    }

    @Override
    public boolean isUnboundWildcard() {
        return TypesUtils.isUnboundWildcard(properType);
    }

    @Override
    public boolean isUpperBoundedWildcard() {
        return TypesUtils.isExtendsBoundWildcard(properType);
    }

    @Override
    public boolean isLowerBoundedWildcard() {
        return TypesUtils.isSuperBoundWildcard(properType);
    }
}
