package org.checkerframework.framework.util.typeinference8.types;

import com.sun.tools.javac.code.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
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
    private final Context context;

    public ProperType(TypeMirror properType, Context context) {
        assert properType != null;
        this.properType = properType;
        this.context = context;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ProperType otherProperType = (ProperType) o;

        return context.factory
                .getContext()
                .getTypeUtils()
                .isSameType(properType, otherProperType.properType);
    }

    @Override
    public int hashCode() {
        int result = properType.toString().hashCode();
        result = 31 * result + Kind.PROPER.hashCode();
        return result;
    }

    public TypeMirror getProperType() {
        return properType;
    }

    @Override
    public AbstractType asSuper(TypeMirror superType) {
        TypeMirror asSuper =
                context.types.asSuper((Type) properType, ((Type) superType).asElement());
        if (asSuper == null) {
            return null;
        }
        return new ProperType(asSuper, context);
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
    public AbstractType getMostSpecificArrayType() {
        TypeMirror mostSpecific = InternalUtils.getMostSpecificArrayType(properType, context.types);
        if (mostSpecific != null) {
            return new ProperType(mostSpecific, context);
        } else {
            return null;
        }
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
            bounds.add(new ProperType(bound, context));
        }
        return bounds;
    }

    @Override
    public AbstractType getTypeVarLowerBound() {
        return new ProperType(((TypeVariable) properType).getLowerBound(), context);
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
    public AbstractType applyInstantiations(List<Instantiation> instantiations) {
        return this;
    }

    @Override
    public List<AbstractType> getFunctionTypeParameters() {
        if (org.checkerframework.javacutil.InternalUtils.isFunctionalInterface(
                properType, context.env)) {
            ExecutableElement element =
                    org.checkerframework.javacutil.InternalUtils.findFunction(
                            (Type) properType, context.env);
            List<AbstractType> params = new ArrayList<>();
            for (VariableElement var : element.getParameters()) {
                params.add(new ProperType(var.asType(), context));
            }
            return params;
        } else {
            return null;
        }
    }

    @Override
    public TypeKind getTypeKind() {
        return properType.getKind();
    }

    public ProperType boxType() {
        if (properType.getKind().isPrimitive()) {
            return new ProperType(context.types.boxedClass((Type) properType).asType(), context);
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
            typeArgs.add(new ProperType(t, context));
        }
        return typeArgs;
    }

    @Override
    public AbstractType getComponentType() {
        if (properType.getKind() != TypeKind.ARRAY) {
            return null;
        }
        return new ProperType(((ArrayType) properType).getComponentType(), context);
    }

    @Override
    public AbstractType getWildcardLowerBound() {
        if (properType.getKind() != TypeKind.WILDCARD) {
            return null;
        } else if (((Type.WildcardType) properType).isSuperBound()) {
            return new ProperType(TypesUtils.wildLowerBound(context.env, properType), context);
        } else {
            return null;
        }
    }

    @Override
    public AbstractType getWildcardUpperBound() {
        if (properType.getKind() != TypeKind.WILDCARD) {
            return null;
        } else if (((Type.WildcardType) properType).isExtendsBound()) {
            TypeMirror upperBound = ((Type.WildcardType) properType).getUpperBound();
            if (upperBound == null) {
                return context.object;
            }
            return new ProperType(upperBound, context);
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

    @Override
    public String toString() {
        return "ProperType: " + properType;
    }
}
