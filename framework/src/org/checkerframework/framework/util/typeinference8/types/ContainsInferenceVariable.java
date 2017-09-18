package org.checkerframework.framework.util.typeinference8.types;

import java.util.Collection;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;

public class ContainsInferenceVariable {

    public static boolean containsInferenceVar(
            Collection<TypeVariable> typeVariables, TypeMirror type) {
        return new Visitor(typeVariables).visit(type);
    }

    static class Visitor implements TypeVisitor<Boolean, Void> {
        private final Collection<TypeVariable> typeVariables;

        Visitor(Collection<TypeVariable> variables) {
            typeVariables = variables;
        }

        private boolean isInferenceVariable(TypeVariable typeVar) {
            return typeVariables.contains(typeVar);
        }

        @Override
        public Boolean visit(TypeMirror t, Void aVoid) {
            return t == null ? false : t.accept(this, aVoid);
        }

        @Override
        public Boolean visit(TypeMirror t) {
            return t == null ? false : t.accept(this, null);
        }

        @Override
        public Boolean visitPrimitive(PrimitiveType t, Void aVoid) {
            return false;
        }

        @Override
        public Boolean visitNull(NullType t, Void aVoid) {
            return false;
        }

        @Override
        public Boolean visitArray(ArrayType t, Void aVoid) {
            return visit(t.getComponentType());
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void aVoid) {
            for (TypeMirror typeArg : t.getTypeArguments()) {
                if (visit(typeArg)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Boolean visitError(ErrorType t, Void aVoid) {
            return null;
        }

        @Override
        public Boolean visitTypeVariable(TypeVariable t, Void aVoid) {
            if (isInferenceVariable(t)) {
                return true;
            }
            if (visit(t.getLowerBound())) {
                return true;
            }
            return visit(t.getUpperBound());
        }

        @Override
        public Boolean visitWildcard(WildcardType t, Void aVoid) {
            if (visit(t.getSuperBound())) {
                return true;
            }
            return visit(t.getExtendsBound());
        }

        @Override
        public Boolean visitExecutable(ExecutableType t, Void aVoid) {
            return false;
        }

        @Override
        public Boolean visitNoType(NoType t, Void aVoid) {
            return false;
        }

        @Override
        public Boolean visitUnknown(TypeMirror t, Void aVoid) {
            return false;
        }

        @Override
        public Boolean visitUnion(UnionType t, Void aVoid) {
            for (TypeMirror altern : t.getAlternatives()) {
                if (visit(altern)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Boolean visitIntersection(IntersectionType t, Void aVoid) {
            for (TypeMirror bound : t.getBounds()) {
                if (visit(bound)) {
                    return true;
                }
            }
            return false;
        }
    }
}
