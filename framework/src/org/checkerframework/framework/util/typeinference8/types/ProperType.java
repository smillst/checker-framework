package org.checkerframework.framework.util.typeinference8.types;

import com.sun.tools.javac.code.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.bound.Instantiation;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.javacutil.TypesUtils;

public class ProperType extends AbstractType {
    private final TypeMirror properType;

    @Override
    public ProperType create(TypeMirror properType) {
        return new ProperType(properType, context);
    }

    public ProperType(TypeMirror properType, Context context) {
        super(context);
        assert properType != null && context != null && properType.getKind() != TypeKind.VOID;
        this.properType = properType;
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

    @Override
    public TypeMirror getJavaType() {
        return properType;
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
    public Collection<Variable> getInferenceVariables() {
        return Collections.emptyList();
    }

    @Override
    public Iterator<ProperType> getTypeParameterBounds() {
        List<ProperType> bounds = new ArrayList<>();
        TypeElement typeelem = (TypeElement) ((DeclaredType) properType).asElement();
        for (TypeParameterElement ele : typeelem.getTypeParameters()) {
            TypeVariable typeVariable = (TypeVariable) ele.asType();
            bounds.add(new ProperType(typeVariable.getUpperBound(), context));
        }
        return bounds.iterator();
    }

    @Override
    public AbstractType applyInstantiations(List<Instantiation> instantiations) {
        return this;
    }

    public ProperType boxType() {
        if (properType.getKind().isPrimitive()) {
            return new ProperType(context.types.boxedClass((Type) properType).asType(), context);
        }
        return this;
    }

    @Override
    public String toString() {
        return properType.toString();
    }
}
