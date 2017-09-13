package org.checkerframework.framework.util.typeinference8.types;

import com.sun.tools.javac.code.Type;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public class ProperType extends AbstractType {
    Types types;
    private final TypeMirror properType;

    public ProperType(TypeMirror properType) {
        this.properType = properType;
    }

    public TypeMirror getProperType() {
        return properType;
    }

    @Override
    public Kind getKind() {
        return Kind.PROPER;
    }

    @Override
    public TypeKind getTypeKind() {
        return properType.getKind();
    }

    public ProperType boxType() {
        if (properType.getKind().isPrimitive()) {
            return new ProperType(types.boxedClass((PrimitiveType) properType).asType());
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
}
