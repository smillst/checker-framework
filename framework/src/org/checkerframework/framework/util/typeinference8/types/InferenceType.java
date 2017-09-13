package org.checkerframework.framework.util.typeinference8.types;

import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.util.InternalUtils;

public class InferenceType extends AbstractType {
    Types types;
    ProcessingEnvironment env;
    final TypeMirror type;
    final Theta map;

    public InferenceType(TypeMirror type, Theta map) {
        this.type = type;
        this.map = map;
    }

    public TypeMirror getType() {
        return type;
    }

    @Override
    public Kind getKind() {
        return Kind.INFERENCE_TYPE;
    }

    @Override
    public AbstractType getComponentType() {
        if (type.getKind() == TypeKind.ARRAY) {
            return InferenceTypeUtil.create(((ArrayType) type).getComponentType(), map);
        } else {
            return null;
        }
    }

    public boolean hasLowerBound() {
        if (type.getKind() == TypeKind.TYPEVAR) {
            return ((TypeVar) type).getLowerBound().getKind() != TypeKind.NULL;
        }
        return false;
    }

    public InferenceType getTypeVarLowerBound() {
        return new InferenceType(((TypeVar) type).getLowerBound(), map);
    }

    public boolean isUnboundWildcard() {
        if (type.getKind() == TypeKind.WILDCARD) {
            return ((WildcardType) type).isUnbound();
        } else {
            return false;
        }
    }

    public boolean isUpperBoundedWildcard() {
        if (type.getKind() == TypeKind.WILDCARD) {
            return ((WildcardType) type).isExtendsBound();
        } else {
            return false;
        }
    }

    public boolean isLowerBoundedWildcard() {
        if (type.getKind() == TypeKind.WILDCARD) {
            return ((WildcardType) type).isSuperBound();
        } else {
            return false;
        }
    }

    public AbstractType getWildcardLowerBound() {
        if (type.getKind() == TypeKind.WILDCARD) {
            return InferenceTypeUtil.create(((WildcardType) type).getLowerBound(), map);
        }
        return null;
    }

    public AbstractType getWildcardUpperBound() {
        if (type.getKind() == TypeKind.WILDCARD) {
            return InferenceTypeUtil.create(((WildcardType) type).getUpperBound(), map);
        }
        return null;
    }

    @Override
    public List<AbstractType> getTypeArguments() {
        if (type.getKind() != TypeKind.DECLARED) {
            return null;
        }
        List<AbstractType> list = new ArrayList<>();
        for (TypeMirror typeArg : ((DeclaredType) type).getTypeArguments()) {
            list.add(InferenceTypeUtil.create(typeArg, map));
        }
        return list;
    }

    @Override
    public TypeKind getTypeKind() {
        return type.getKind();
    }

    /**
     * whether the proper type is a parameterized class or interface type, or an inner class type of
     * a parameterized class or interface type (directly or indirectly)
     *
     * @return whether T is a parametrized type.
     */
    public boolean isParameterizedType() {
        return InferenceTypeUtil.isParameterizedType(type);
    }

    public AbstractType getMostSpecificArrayType() {
        return InferenceTypeUtil.create(getMostSpecificArrayType(type, types), map);
    }

    private TypeMirror getMostSpecificArrayType(TypeMirror type, Types types) {
        if (type.getKind() == TypeKind.ARRAY) {
            return type;
        } else {
            for (TypeMirror superType : types.directSupertypes(type)) {
                TypeMirror arrayType = getMostSpecificArrayType(superType, types);
                if (arrayType != null) {
                    return arrayType;
                }
            }
            return null;
        }
    }

    public boolean isPrimitiveArray() {
        if (type.getKind() == TypeKind.ARRAY) {
            return ((ArrayType) type).getComponentType().getKind().isPrimitive();
        } else {
            return false;
        }
    }

    public List<AbstractType> getIntersectionBounds() {
        List<AbstractType> boundTypes = new ArrayList<>();
        if (type.getKind() == TypeKind.INTERSECTION) {
            for (TypeMirror boundType : ((IntersectionType) type).getBounds()) {
                boundTypes.add(InferenceTypeUtil.create(boundType, map));
            }
        }
        return boundTypes;
    }

    public AbstractType applyInstantiation(Instantiation instantiation) {
        TypeMirror newType =
                InternalUtils.subs(
                        env, type, instantiation.getA().p, instantiation.getT().getProperType());
        return InferenceTypeUtil.create(newType, map);
    }

    /** @return all inference variables mentioned in this type. */
    @Override
    public Collection<? extends Variable> getInferenceVariables() {
        // TODO: implement
        throw new RuntimeException("not implemented");
    }
}
