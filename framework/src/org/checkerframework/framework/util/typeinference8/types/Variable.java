package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.bound.Equal.Instantiation;
import org.checkerframework.framework.util.typeinference8.util.Context;

public class Variable extends AbstractType {
    protected final TypeVariable typeVariable;
    /** The expression for which this variable is being solved. */
    protected final ExpressionTree invocation;

    protected final Context context;

    public Variable(TypeVariable typeVariable, ExpressionTree invocation, Context context) {
        assert typeVariable != null;
        this.typeVariable = typeVariable;
        this.invocation = invocation;
        this.context = context;
    }

    public TypeVariable getTypeVariable() {
        return typeVariable;
    }

    public ExpressionTree getInvocation() {
        return invocation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Variable variable = (Variable) o;
        if (context.factory
                .getContext()
                .getTypeUtils()
                .isSameType(typeVariable, variable.typeVariable)) {
            return invocation == variable.invocation;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = typeVariable.toString().hashCode();
        result = 31 * result + Kind.VARIABLE.hashCode();
        return result;
    }

    @Override
    public AbstractType asSuper(TypeMirror superType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Kind getKind() {
        return Kind.VARIABLE;
    }

    @Override
    public boolean isParameterizedType() {
        return false;
    }

    @Override
    public AbstractType getMostSpecificArrayType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPrimitiveArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUpperBoundedWildcard() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AbstractType> getIntersectionBounds() {
        return null;
    }

    @Override
    public AbstractType getTypeVarLowerBound() {
        return null;
    }

    @Override
    public boolean hasLowerBound() {
        return false;
    }

    @Override
    public Collection<Variable> getInferenceVariables() {
        return Collections.singleton(this);
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
        for (Instantiation inst : instantiations) {
            if (inst.getA().equals(this)) {
                return inst.getT();
            }
        }
        return this;
    }

    @Override
    public AbstractType getFunctionTypeReturn() {
        return null;
    }

    @Override
    public boolean isRaw() {
        return false;
    }

    @Override
    public List<AbstractType> getFunctionTypeParameters() {
        return null;
    }

    @Override
    public TypeKind getTypeKind() {
        return TypeKind.TYPEVAR;
    }

    @Override
    public List<AbstractType> getTypeArguments() {
        return null;
    }

    @Override
    public AbstractType getComponentType() {
        return null;
    }

    @Override
    public AbstractType getWildcardUpperBound() {
        return null;
    }

    @Override
    public AbstractType getWildcardLowerBound() {
        return null;
    }

    @Override
    public boolean isUnboundWildcard() {
        return false;
    }

    @Override
    public boolean isLowerBoundedWildcard() {
        return false;
    }

    @Override
    public String toString() {
        return "var " + typeVariable + " for " + invocation;
    }

    public static class CaptureVariable extends Variable {

        public CaptureVariable(TypeVariable type, ExpressionTree invocation, Context context) {
            super(type, invocation, context);
        }

        @Override
        public String toString() {
            return "capture var " + typeVariable + " for " + invocation;
        }
    }
}
