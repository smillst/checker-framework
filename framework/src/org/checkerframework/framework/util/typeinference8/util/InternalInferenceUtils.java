package org.checkerframework.framework.util.typeinference8.util;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberReferenceTree.ReferenceMode;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.JCMemberReference;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypeAnnotationUtils;
import org.checkerframework.javacutil.TypesUtils;

public class InternalInferenceUtils {

    public static Types getTypes(ProcessingEnvironment env) {
        JavacProcessingEnvironment javacEnv = (JavacProcessingEnvironment) env;
        return Types.instance(javacEnv.getContext());
    }

    /**
     * Returns a new type mirror with the same type as {@code type} where all the type variables in
     * {@code typeVariables} have been substituted with the type arguments in {@code typeArgs}.
     *
     * <p>This is a wrapper around {@link Types#subst(com.sun.tools.javac.code.Type,
     * com.sun.tools.javac.util.List, com.sun.tools.javac.util.List)}
     *
     * @return a new type mirror with the same type as {@code type} where all the type variables in
     *     {@code typeVariables} have been substituted with the type arguments in {@code typeArgs}.
     */
    public static TypeMirror substitute(
            TypeMirror type,
            List<? extends TypeVariable> typeVariables,
            List<? extends TypeMirror> typeArgs,
            ProcessingEnvironment env) {

        List<Type> newP = new ArrayList<>();
        for (TypeVariable typeVariable : typeVariables) {
            newP.add((Type) typeVariable);
        }

        List<Type> newT = new ArrayList<>();
        for (TypeMirror typeMirror : typeArgs) {
            newT.add((Type) typeMirror);
        }

        Types types = getTypes(env);
        return types.subst(
                (Type) type,
                com.sun.tools.javac.util.List.from(newP),
                com.sun.tools.javac.util.List.from(newT));
    }

    public static boolean isPolyExpression(ExpressionTree expression) {
        if (expression instanceof com.sun.tools.javac.tree.JCTree.JCExpression) {
            return ((com.sun.tools.javac.tree.JCTree.JCExpression) expression).isPoly();
        }
        return false;
    }

    public static boolean isStandaloneExpression(ExpressionTree expression) {
        if (expression instanceof com.sun.tools.javac.tree.JCTree.JCExpression) {
            return ((com.sun.tools.javac.tree.JCTree.JCExpression) expression).isStandalone();
        }
        return false;
    }

    public static TypeMirror lub(
            ProcessingEnvironment processingEnv, TypeMirror tm1, TypeMirror tm2) {
        Type t1 = TypeAnnotationUtils.unannotatedType(tm1);
        Type t2 = TypeAnnotationUtils.unannotatedType(tm2);
        JavacProcessingEnvironment javacEnv = (JavacProcessingEnvironment) processingEnv;
        Types types = Types.instance(javacEnv.getContext());

        return types.lub(t1, t2);
    }

    public static TypeMirror glb(
            ProcessingEnvironment processingEnv, TypeMirror tm1, TypeMirror tm2) {
        Type t1 = TypeAnnotationUtils.unannotatedType(tm1);
        Type t2 = TypeAnnotationUtils.unannotatedType(tm2);
        JavacProcessingEnvironment javacEnv = (JavacProcessingEnvironment) processingEnv;
        Types types = Types.instance(javacEnv.getContext());

        return types.glb(t1, t2);
    }

    public static TypeMirror getMostSpecificArrayType(TypeMirror type, Types types) {
        if (type.getKind() == TypeKind.ARRAY) {
            return type;
        } else {
            for (TypeMirror superType : types.directSupertypes((Type) type)) {
                TypeMirror arrayType = getMostSpecificArrayType(superType, types);
                if (arrayType != null) {
                    return arrayType;
                }
            }
            return null;
        }
    }

    public static boolean isParameterized(TypeMirror result) {
        return ((Type) result).isParameterized();
    }

    public static boolean isWildcardParameterized(TypeMirror result) {
        if (isParameterized(result) && result.getKind() == TypeKind.DECLARED) {
            for (TypeMirror t : ((DeclaredType) result).getTypeArguments()) {
                if (t.getKind() == TypeKind.WILDCARD) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isVarArgMethodCall(ExpressionTree methodInvocation) {
        if (methodInvocation.getKind() == Kind.METHOD_INVOCATION) {
            return ((JCMethodInvocation) methodInvocation).varargsElement != null;
        } else if (methodInvocation.getKind() == Kind.NEW_CLASS) {
            return ((JCNewClass) methodInvocation).varargsElement != null;
        } else {
            return false;
        }
    }

    public static DeclaredType getReceiverType(ExpressionTree tree) {
        Tree receiverTree = TreeUtils.getReceiverTree(tree);
        if (receiverTree == null) {
            return null;
        }
        TypeMirror type = TreeUtils.typeOf(receiverTree);
        if (type.getKind() == TypeKind.TYPEVAR) {
            return (DeclaredType) ((TypeVariable) type).getUpperBound();
        }
        // TODO: this must exist else where.....
        return type.getKind() == TypeKind.DECLARED ? (DeclaredType) type : null;
    }

    /**
     * @return ExecutableType of the method invocation or new class tree adapted to the call site.
     */
    public static ExecutableType getTypeOfMethodAdaptedToUse(
            ExpressionTree expressionTree, Context context) {
        if (expressionTree.getKind() == Kind.NEW_CLASS) {
            return (ExecutableType) ((JCNewClass) expressionTree).constructorType;
        } else if (expressionTree.getKind() != Kind.METHOD_INVOCATION) {
            return null;
        }

        ExecutableElement ele = (ExecutableElement) TreeUtils.elementFromUse(expressionTree);
        if (ElementUtils.isStatic(ele)) {
            return (ExecutableType) ele.asType();
        }
        DeclaredType receiverType = getReceiverType(expressionTree);
        if (receiverType == null) {
            receiverType = context.enclosingType;
        }

        while (context.types.asSuper((Type) receiverType, (Symbol) ele.getEnclosingElement())
                == null) {
            TypeMirror enclosing = receiverType.getEnclosingType();
            if (enclosing == null || enclosing.getKind() != TypeKind.DECLARED) {
                ErrorReporter.errorAbort("Method not found");
            }
            receiverType = (DeclaredType) enclosing;
        }
        javax.lang.model.util.Types types = context.env.getTypeUtils();
        return (ExecutableType) types.asMemberOf(receiverType, ele);
    }

    public static TypeMirror getFreshTypeVar(
            Context context, TypeMirror lowerBound, TypeMirror upperBound) {
        if (lowerBound != null) {
            if (TypesUtils.isObject(upperBound)) {
                upperBound = null;
            }
        }

        assert lowerBound == null || upperBound == null;
        WildcardType wildcardType =
                context.env.getTypeUtils().getWildcardType(upperBound, lowerBound);
        //        return context.types.freshTypeVariables(
        return com.sun.tools.javac.util.List.of((Type) wildcardType).head;
    }

    /**
     * @return a supertype of S of the form G<S1, ..., Sn> and a supertype of T of the form
     *     G<T1,..., Tn> for some generic class or interface, G. If such types exist; otherwise,
     *     null is returned.
     */
    public static Pair<TypeMirror, TypeMirror> getParameterizedSupers(
            Context context, TypeMirror s, TypeMirror t) {
        // com.sun.tools.javac.comp.Infer#getParameterizedSupers
        TypeMirror lubResult = lub(context.env, t, s);
        if (!isParameterized(lubResult)) {
            return null;
        }

        Type asSuperOfT = context.types.asSuper((Type) t, ((Type) lubResult).asElement());
        Type asSuperOfS = context.types.asSuper((Type) s, ((Type) lubResult).asElement());
        return Pair.of(asSuperOfT, asSuperOfS);
    }

    /**
     * JLS 15.13.1: "The compile-time declaration of a method reference is the method to which the
     * expression refers."
     *
     * @param memberReferenceTree method reference
     * @param env processing environment
     * @return method to which the expression refers
     */
    public static ExecutableType compileTimeDeclarationType(
            MemberReferenceTree memberReferenceTree, ProcessingEnvironment env) {
        ExecutableType type;
        if (memberReferenceTree.getMode() == ReferenceMode.NEW) {
            TypeMirror functionalType = TreeUtils.typeOf(memberReferenceTree);
            return TypesUtils.findFunctionType(functionalType, env);
        }
        // else Member reference refers to a method rather than a constructor.
        // In this case, the compile-time declaration is ((JCMemberReference) memberReferenceTree).sym.
        // However, to get the correct type, the declaration has to be modified based on the use.
        ExecutableElement ctDecl =
                (ExecutableElement) ((JCMemberReference) memberReferenceTree).sym;
        switch (((JCMemberReference) memberReferenceTree).kind) {
            case UNBOUND: // ref is of form: Type :: instance method
                TypeMirror functionalType = TreeUtils.typeOf(memberReferenceTree);
                ExecutableType functionType = TypesUtils.findFunctionType(functionalType, env);
                DeclaredType receiver = (DeclaredType) functionType.getParameterTypes().get(0);
                type = (ExecutableType) env.getTypeUtils().asMemberOf(receiver, ctDecl);
                break;
            case BOUND: // ref is of form: expression :: method
            case SUPER: // ref is of form: super :: method
                TypeMirror expr = TreeUtils.typeOf(((JCMemberReference) memberReferenceTree).expr);
                type = (ExecutableType) getTypes(env).memberType((Type) expr, (Symbol) ctDecl);
                break;
            default: // ref is of form: Type :: static method
                type = (ExecutableType) ctDecl.asType();
        }

        if (memberReferenceTree.getTypeArguments() == null
                || memberReferenceTree.getTypeArguments().isEmpty()) {
            return type;
        }
        List<TypeMirror> args = new ArrayList<>();
        for (ExpressionTree tree : memberReferenceTree.getTypeArguments()) {
            args.add(TreeUtils.typeOf(tree));
        }
        return (ExecutableType) substitute(type, type.getTypeVariables(), args, env);
    }

    public static boolean isCaptured(TypeMirror type) {
        if (type.getKind() != TypeKind.TYPEVAR) {
            return false;
        }
        return TypesUtils.isCaptured((TypeVariable) type);
    }
}
