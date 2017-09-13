package org.checkerframework.framework.util.typeinference8.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public class InferenceTypeUtil {
    public static boolean containsInferenceVar(
            Collection<TypeVariable> typeVariables, TypeMirror type) {
        return ContainsInferenceVariable.containsInferenceVar(typeVariables, type);
    }

    public static AbstractType create(TypeMirror type, Theta map) {
        if (type.getKind() == TypeKind.TYPEVAR && map.containsKey(type)) {
            return map.get(type);
        } else if (containsInferenceVar(map.keySet(), type)) {
            return new InferenceType(type, map);
        } else {
            return new ProperType(type);
        }
    }

    public static List<AbstractType> create(List<? extends TypeMirror> types, Theta map) {
        List<AbstractType> abstractTypes = new ArrayList<>();
        for (TypeMirror type : types) {
            abstractTypes.add(create(type, map));
        }
        return abstractTypes;
    }

    public static boolean isParameterizedType(TypeMirror type) {
        if (type == null || type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) type;
        return !declaredType.getTypeArguments().isEmpty()
                || isParameterizedType(declaredType.getEnclosingType());
    }
}
