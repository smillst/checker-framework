package org.checkerframework.framework.util.typeinference8.types;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.CapturedType;
import com.sun.tools.javac.code.Type.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.framework.util.typeinference8.util.InternalInferenceUtils;
import org.checkerframework.javacutil.TypesUtils;

public abstract class AbstractType {
    protected final Context context;

    public AbstractType(Context context) {
        this.context = context;
    }

    public abstract AbstractType create(TypeMirror type);

    public AbstractType capture() {
        TypeMirror capture = context.env.getTypeUtils().capture(getJavaType());
        return create(capture);
    }

    public AbstractType unCapture() {
        if (InternalInferenceUtils.isCaptured(getJavaType())) {
            TypeMirror wildcard = ((CapturedType) getJavaType()).wildcard;
            return create(wildcard);
        } else {
            return null;
        }
    }

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

    public final boolean isInferenceType() {
        return getKind() == Kind.INFERENCE_TYPE;
    }

    public abstract TypeMirror getJavaType();

    public abstract Collection<Variable> getInferenceVariables();

    public abstract Iterator<ProperType> getTypeParameterBounds();

    public abstract AbstractType applyInstantiations(List<Variable> instantiations);

    public abstract boolean isObject();

    /**
     * Returns null if superType isn't a super type.
     *
     * @param superType
     * @return
     */
    public final AbstractType asSuper(TypeMirror superType) {
        TypeMirror type = getJavaType();
        if (type.getKind() == TypeKind.WILDCARD) {
            type = ((WildcardType) type).getExtendsBound();
        }
        TypeMirror asSuper = context.types.asSuper((Type) type, ((Type) superType).asElement());
        if (asSuper == null) {
            return null;
        }
        return create(asSuper);
    }

    public final AbstractType getFunctionTypeReturn() {
        if (TypesUtils.isFunctionalInterface(getJavaType(), context.env)) {
            ExecutableType element = TypesUtils.findFunctionType(getJavaType(), context.env);
            TypeMirror returnType = element.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) {
                return null;
            }
            return create(returnType);
        } else {
            return null;
        }
    }

    public final List<AbstractType> getFunctionTypeParameters() {
        if (TypesUtils.isFunctionalInterface(getJavaType(), context.env)) {
            ExecutableType element = TypesUtils.findFunctionType(getJavaType(), context.env);
            List<AbstractType> params = new ArrayList<>();
            for (TypeMirror param : element.getParameterTypes()) {
                params.add(create(param));
            }
            return params;
        } else {
            return null;
        }
    }

    public final boolean isRaw() {
        return InternalInferenceUtils.isRaw(getJavaType());
    }

    public final AbstractType replaceTypeArgs(List<AbstractType> args) {
        DeclaredType declaredType = (DeclaredType) getJavaType();
        TypeMirror[] newArgs = new TypeMirror[args.size()];
        int i = 0;
        for (AbstractType t : args) {
            newArgs[i++] = t.getJavaType();
        }
        TypeMirror newType =
                context.env
                        .getTypeUtils()
                        .getDeclaredType((TypeElement) declaredType.asElement(), newArgs);
        return create(newType);
    }

    /**
     * whether the proper type is a parameterized class or interface type, or an inner class type of
     * a parameterized class or interface type (directly or indirectly)
     *
     * @return whether T is a parametrized type.
     */
    public final boolean isParameterizedType() {
        // TODO this isn't matching the JavaDoc.
        return ((Type) getJavaType()).isParameterized();
    }

    public final AbstractType getMostSpecificArrayType() {
        TypeMirror mostSpecific =
                InternalInferenceUtils.getMostSpecificArrayType(getJavaType(), context.types);
        if (mostSpecific != null) {
            return create(mostSpecific);
        } else {
            return null;
        }
    }

    public final boolean isPrimitiveArray() {
        return getJavaType().getKind() == TypeKind.ARRAY
                && ((ArrayType) getJavaType()).getComponentType().getKind().isPrimitive();
    }

    public final List<AbstractType> getIntersectionBounds() {
        List<AbstractType> bounds = new ArrayList<>();
        for (TypeMirror bound : ((IntersectionType) getJavaType()).getBounds()) {
            bounds.add(create(bound));
        }
        return bounds;
    }

    public final AbstractType getTypeVarLowerBound() {
        return create(((TypeVariable) getJavaType()).getLowerBound());
    }

    public final boolean hasLowerBound() {
        return ((TypeVariable) getJavaType()).getLowerBound().getKind() != TypeKind.NULL;
    }

    public final boolean isWildcardParameterizedType() {
        return InternalInferenceUtils.isWildcardParameterized(getJavaType());
    }

    public final TypeKind getTypeKind() {
        return getJavaType().getKind();
    }

    public final List<AbstractType> getTypeArguments() {
        if (getJavaType().getKind() != TypeKind.DECLARED) {
            return null;
        }
        List<AbstractType> list = new ArrayList<>();
        for (TypeMirror typeArg : ((DeclaredType) getJavaType()).getTypeArguments()) {
            list.add(create(typeArg));
        }
        return list;
    }

    public final boolean isUnboundWildcard() {
        if (getJavaType().getKind() == TypeKind.WILDCARD) {
            return ((WildcardType) getJavaType()).isUnbound();
        } else {
            return false;
        }
    }

    public final boolean isUpperBoundedWildcard() {
        if (getJavaType().getKind() == TypeKind.WILDCARD) {
            return ((WildcardType) getJavaType()).isExtendsBound();
        } else {
            return false;
        }
    }

    public final boolean isLowerBoundedWildcard() {
        if (getJavaType().getKind() == TypeKind.WILDCARD) {
            return ((WildcardType) getJavaType()).isSuperBound();
        } else {
            return false;
        }
    }

    public final AbstractType getWildcardLowerBound() {
        if (getJavaType().getKind() == TypeKind.WILDCARD) {
            return create(TypesUtils.wildLowerBound(getJavaType(), context.env));
        }
        return null;
    }

    public final AbstractType getWildcardUpperBound() {
        if (getJavaType().getKind() != TypeKind.WILDCARD) {
            return null;
        } else if (((Type.WildcardType) getJavaType()).isExtendsBound()) {
            TypeMirror upperBound = ((WildcardType) getJavaType()).getExtendsBound();
            if (upperBound == null) {
                return context.object;
            }
            return create(upperBound);
        } else {
            return null;
        }
    }

    public AbstractType getErased() {
        return create(context.env.getTypeUtils().erasure(getJavaType()));
    }

    public final AbstractType getComponentType() {
        if (getJavaType().getKind() == TypeKind.ARRAY) {
            return create(((ArrayType) getJavaType()).getComponentType());
        } else {
            return null;
        }
    }
}
