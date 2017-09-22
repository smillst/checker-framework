package org.checkerframework.framework.util.typeinference8.types;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
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

public class InferenceType extends AbstractType {
    private final TypeMirror type;
    private final Theta map;

    InferenceType(TypeMirror type, Theta map) {
        this.type = type;
        this.map = map;
    }

    public TypeMirror getType() {
        return type;
    }

    @Override
    public AbstractType asSuper(TypeMirror superType, Context context) {
        TypeMirror asSuper = context.types.asSuper((Type) type, ((Type) superType).asElement());
        if (asSuper == null) {
            return null;
        }
        return InferenceTypeUtil.create(asSuper, map);
    }

    @Override
    public boolean isObject() {
        return false;
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

    public AbstractType getTypeVarLowerBound() {
        return InferenceTypeUtil.create(((TypeVar) type).getLowerBound(), map);
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

    @Override
    public AbstractType getWildcardLowerBound() {
        if (type.getKind() == TypeKind.WILDCARD) {
            return InferenceTypeUtil.create(((WildcardType) type).getLowerBound(), map);
        }
        return null;
    }

    @Override
    public AbstractType getWildcardUpperBound(Context context) {
        if (type.getKind() == TypeKind.WILDCARD) {
            TypeMirror upperBound = ((WildcardType) type).getUpperBound();
            if (upperBound == null) {
                upperBound = context.object;
            }
            return InferenceTypeUtil.create(upperBound, map);
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

    public AbstractType getMostSpecificArrayType(Context context) {
        return InferenceTypeUtil.create(
                InternalUtils.getMostSpecificArrayType(type, context.types), map);
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

    /** @return all inference variables mentioned in this type. */
    @Override
    public Collection<? extends Variable> getInferenceVariables() {
        LinkedHashSet<Variable> variables = new LinkedHashSet<>();
        for (TypeVariable typeVar : ContainsInferenceVariable.getInferenceVar(map.keySet(), type)) {
            variables.add(map.get(typeVar));
        }
        return variables;
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
        List<TypeVariable> typeVariables = new ArrayList<>(instantiations.size());
        List<TypeMirror> arguments = new ArrayList<>(instantiations.size());

        for (Instantiation instantiation : instantiations) {
            typeVariables.add(instantiation.getA().p);
            arguments.add(instantiation.getT().getProperType());
        }

        TypeMirror newType = InternalUtils.subs(context.env, type, typeVariables, arguments);
        return InferenceTypeUtil.create(newType, map);
    }

    @Override
    public List<AbstractType> getFunctionTypeParameters() {
        return null;
    }

    @Override
    public String toString() {
        return "InferenceType: " + type;
    }
}
