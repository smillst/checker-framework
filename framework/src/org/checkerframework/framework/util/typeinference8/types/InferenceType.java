package org.checkerframework.framework.util.typeinference8.types;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.bound.Instantiation;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InferenceUtils;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.TypesUtils;

public class InferenceType extends AbstractType {
    private final TypeMirror type;
    private final Theta map;
    private final Context context;

    InferenceType(TypeMirror type, Theta map, Context context) {
        this.type = type;
        this.map = map;
        this.context = context;
    }

    public Theta getMap() {
        return map;
    }

    public Context getContext() {
        return context;
    }

    public static boolean containsInferenceVar(
            Collection<TypeVariable> typeVariables, TypeMirror type) {
        return ContainsInferenceVariable.hasAnyInferenceVar(typeVariables, type);
    }

    public static AbstractType create(TypeMirror type, Theta map, Context context) {
        assert type != null;
        if (type.getKind() == TypeKind.TYPEVAR && map.containsKey(type)) {
            return map.get(type);
        } else if (containsInferenceVar(map.keySet(), type)) {
            return new InferenceType(type, map, context);
        } else {
            return new ProperType(type, context);
        }
    }

    public static List<AbstractType> create(
            List<? extends TypeMirror> types, Theta map, Context context) {
        List<AbstractType> abstractTypes = new ArrayList<>();
        for (TypeMirror type : types) {
            abstractTypes.add(create(type, map, context));
        }
        return abstractTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InferenceType variable = (InferenceType) o;
        return context.factory.getContext().getTypeUtils().isSameType(type, variable.type);
    }

    @Override
    public int hashCode() {
        int result = type.toString().hashCode();
        result = 31 * result + Kind.INFERENCE_TYPE.hashCode();
        return result;
    }

    public TypeMirror getJavaType() {
        return type;
    }

    @Override
    public AbstractType asSuper(TypeMirror superType) {
        TypeMirror asSuper = context.types.asSuper((Type) type, ((Type) superType).asElement());
        if (asSuper == null) {
            return null;
        }
        return create(asSuper, map, context);
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
            return create(((ArrayType) type).getComponentType(), map, context);
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
        return create(((TypeVar) type).getLowerBound(), map, context);
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
            return create(TypesUtils.wildLowerBound(type, context.env), map, context);
        }
        return null;
    }

    @Override
    public AbstractType getWildcardUpperBound() {
        if (type.getKind() == TypeKind.WILDCARD) {
            TypeMirror upperBound = ((WildcardType) type).getExtendsBound();
            if (upperBound == null) {
                return context.object;
            }
            return create(upperBound, map, context);
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
            list.add(create(typeArg, map, context));
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
        return InferenceUtils.isParameterizedType(type);
    }

    /** @return the most specific array type or null if there isn't one. */
    public AbstractType getMostSpecificArrayType() {
        TypeMirror mostSpecific =
                InternalInferenceUtils.getMostSpecificArrayType(type, context.types);
        if (mostSpecific != null) {
            return create(mostSpecific, map, context);
        } else {
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
                boundTypes.add(create(boundType, map, context));
            }
        }
        return boundTypes;
    }

    /** @return all inference variables mentioned in this type. */
    @Override
    public Collection<Variable> getInferenceVariables() {
        LinkedHashSet<Variable> variables = new LinkedHashSet<>();
        for (TypeVariable typeVar : ContainsInferenceVariable.getInferenceVar(map.keySet(), type)) {
            variables.add(map.get(typeVar));
        }
        return variables;
    }

    @Override
    public Iterator<ProperType> getTypeParameterBounds() {
        List<ProperType> bounds = new ArrayList<>();
        TypeElement typeelem = (TypeElement) ((DeclaredType) type).asElement();
        for (TypeParameterElement ele : typeelem.getTypeParameters()) {
            TypeVariable typeVariable = (TypeVariable) ele.asType();
            bounds.add(new ProperType(typeVariable.getUpperBound(), context));
        }
        return bounds.iterator();
    }

    @Override
    public AbstractType replaceTypeArgs(List<AbstractType> args) {
        DeclaredType declaredType = (DeclaredType) type;
        TypeMirror[] newArgs = new TypeMirror[args.size()];
        int i = 0;
        for (AbstractType t : args) {
            newArgs[i++] = t.getJavaType();
        }
        TypeMirror newType =
                context.env
                        .getTypeUtils()
                        .getDeclaredType((TypeElement) declaredType.asElement(), newArgs);
        return create(newType, map, context);
    }

    @Override
    public boolean isWildcardParameterizedType() {
        return InternalInferenceUtils.isWildcardParameterized(type);
    }

    @Override
    public AbstractType applyInstantiations(List<Instantiation> instantiations) {
        List<TypeVariable> typeVariables = new ArrayList<>(instantiations.size());
        List<TypeMirror> arguments = new ArrayList<>(instantiations.size());

        for (Instantiation instantiation : instantiations) {
            Variable alpha = instantiation.getA();
            if (map.containsValue(alpha)) {
                typeVariables.add(instantiation.getA().getJavaType());
                arguments.add(instantiation.getT().getJavaType());
            }
        }
        if (typeVariables.isEmpty()) {
            return this;
        }

        TypeMirror newType =
                InternalInferenceUtils.subs(context.env, type, typeVariables, arguments);
        return create(newType, map, context);
    }

    @Override
    public AbstractType getFunctionTypeReturn() {
        if (TypesUtils.isFunctionalInterface(type, context.env)) {
            ExecutableType element = TypesUtils.findFunctionType(type, context.env);
            TypeMirror returnType = element.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) {
                return null;
            }
            return InferenceType.create(returnType, map, context);
        } else {
            return null;
        }
    }

    @Override
    public boolean isRaw() {
        return InternalInferenceUtils.isRaw(type);
    }

    @Override
    public AbstractType getErased() {
        return create(context.env.getTypeUtils().erasure(type), map, context);
    }

    @Override
    public List<AbstractType> getFunctionTypeParameters() {
        if (TypesUtils.isFunctionalInterface(type, context.env)) {
            ExecutableType element = TypesUtils.findFunctionType(type, context.env);
            List<AbstractType> params = new ArrayList<>();
            for (TypeMirror param : element.getParameterTypes()) {
                params.add(InferenceType.create(param, map, context));
            }
            return params;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "inference type: " + type;
    }
}
