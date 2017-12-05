package org.checkerframework.framework.util.typeinference8.types;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.TypesUtils;

public class ProperType extends AbstractType {
    private final TypeMirror properType;
    private final Context context;

    public ProperType(TypeMirror properType, Context context) {
        assert properType != null && context != null && properType.getKind() != TypeKind.VOID;
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
        if (properType == otherProperType.properType) {
            return true;
        }
        return context.env.getTypeUtils().isSameType(properType, otherProperType.properType);
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
        TypeMirror mostSpecific =
                InternalInferenceUtils.getMostSpecificArrayType(properType, context.types);
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
    public Collection<Variable> getInferenceVariables() {
        return Collections.emptyList();
    }

    @Override
    public Iterator<ProperType> getTypeParameterBounds() {
        List<ProperType> bounds = new ArrayList<>();
        TypeElement typeelem = (TypeElement) ((DeclaredType) properType).asElement();
        for (TypeParameterElement ele : typeelem.getTypeParameters()) {
            bounds.add(new ProperType(ele.asType(), context));
        }
        return bounds.iterator();
    }

    @Override
    public AbstractType replaceTypeArgs(List<AbstractType> args) {
        DeclaredType declaredType = (DeclaredType) properType;
        TypeMirror[] newArgs = new TypeMirror[args.size()];
        int i = 0;
        for (AbstractType t : args) {
            newArgs[i++] = ((ProperType) t).properType;
        }
        TypeMirror newType =
                context.env
                        .getTypeUtils()
                        .getDeclaredType((TypeElement) declaredType.asElement(), newArgs);
        return new ProperType(newType, context);
    }

    @Override
    public boolean isWildcardParameterizedType() {
        return InternalInferenceUtils.isWildcardParameterized(properType);
    }

    @Override
    public AbstractType applyInstantiations(List<Instantiation> instantiations) {
        return this;
    }

    @Override
    public AbstractType getFunctionTypeReturn() {
        if (TypesUtils.isFunctionalInterface(properType, context.env)) {
            ExecutableType element = TypesUtils.findFunctionType(properType, context.env);
            TypeMirror returnType = element.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) {
                return null;
            }
            return new ProperType(returnType, context);
        } else {
            return null;
        }
    }

    @Override
    public List<AbstractType> getFunctionTypeParameters() {
        if (TypesUtils.isFunctionalInterface(properType, context.env)) {
            ExecutableType element = TypesUtils.findFunctionType(properType, context.env);
            List<AbstractType> params = new ArrayList<>();
            for (TypeMirror param : element.getParameterTypes()) {
                params.add(new ProperType(param, context));
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
            return new ProperType(TypesUtils.wildLowerBound(properType, context.env), context);
        } else {
            return null;
        }
    }

    @Override
    public AbstractType getWildcardUpperBound() {
        if (properType.getKind() != TypeKind.WILDCARD) {
            return null;
        } else if (((Type.WildcardType) properType).isExtendsBound()) {
            TypeMirror upperBound = ((WildcardType) properType).getExtendsBound();
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
        return properType.toString();
    }

    @Override
    public boolean isRaw() {
        return InternalInferenceUtils.isRaw(properType);
    }
}
