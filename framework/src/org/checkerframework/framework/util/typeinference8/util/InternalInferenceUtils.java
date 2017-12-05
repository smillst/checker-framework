package org.checkerframework.framework.util.typeinference8.util;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LambdaExpressionTree.BodyKind;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCLambda.ParameterKind;
import com.sun.tools.javac.tree.JCTree.JCMemberReference;
import com.sun.tools.javac.tree.JCTree.JCMemberReference.OverloadKind;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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

    public static TypeMirror subs(
            ProcessingEnvironment env, TypeMirror type, List<TypeVariable> p, List<TypeMirror> t) {

        List<Type> newP = new ArrayList<>();
        for (TypeVariable typeVariable : p) {
            newP.add((Type) typeVariable);
        }

        List<Type> newT = new ArrayList<>();
        for (TypeMirror typeMirror : t) {
            newT.add((Type) typeMirror);
        }

        Types types = getTypes(env);
        return types.subst(
                (Type) type,
                com.sun.tools.javac.util.List.from(newP),
                com.sun.tools.javac.util.List.from(newT));
    }

    public static boolean isImplicitlyType(Tree lambdaTree) {
        return lambdaTree.getKind() == Kind.LAMBDA_EXPRESSION
                && ((JCLambda) lambdaTree).paramKind == ParameterKind.IMPLICIT;
    }

    public static boolean isExplicitlyType(Tree lambdaTree) {
        return lambdaTree.getKind() == Kind.LAMBDA_EXPRESSION
                && ((JCLambda) lambdaTree).paramKind == ParameterKind.EXPLICIT;
    }

    public static List<ExpressionTree> getReturnedExpressions(LambdaExpressionTree lambda) {
        // TODO: Does this method already exist somewhere??
        if (lambda.getBodyKind() == BodyKind.EXPRESSION) {
            return Collections.singletonList((ExpressionTree) lambda.getBody());
        }

        List<ExpressionTree> list = new ArrayList<>();
        BlockTree body = (BlockTree) lambda.getBody();
        for (StatementTree statement : body.getStatements()) {
            if (statement.getKind() == Kind.RETURN) {
                ReturnTree returnTree = (ReturnTree) statement;
                list.add(returnTree.getExpression());
            }
        }
        return list;
    }

    public static boolean isExact(MemberReferenceTree ref) {
        // Seems like overloaded means the same thing as inexact.
        // overloadKind is set com.sun.tools.javac.comp.DeferredAttr.DeferredChecker.visitReference()
        // IsExact: https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.13.1-400
        return ((JCMemberReference) ref).overloadKind != OverloadKind.OVERLOADED;
    }

    public static boolean isGenericMethod(MemberReferenceTree ref) {
        // TODO: implement
        throw new RuntimeException("Not implemented");
    }

    /**
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.2.2 (Assuming the
     * method is a generic method and the method invocation does not provide explicit type
     * arguments)
     *
     * @param expressionTree
     * @param b whether the corresponding target type (as derived from the signature of m) is a type
     *     parameter of m
     * @return
     */
    public static boolean notPertinentToApplicability(ExpressionTree expressionTree, boolean b) {
        switch (expressionTree.getKind()) {
            case LAMBDA_EXPRESSION:
                LambdaExpressionTree lambda = (LambdaExpressionTree) expressionTree;
                if (isImplicitlyType(lambda) || b) {
                    // An implicitly typed lambda expression.
                    return true;
                } else {
                    // An explicitly typed lambda expression whose body is a block,
                    // where at least one result expression is not pertinent to applicability.
                    // An explicitly typed lambda expression whose body is an expression that is not pertinent to applicability.
                    for (ExpressionTree result : getReturnedExpressions(lambda)) {
                        if (notPertinentToApplicability(result, b)) {
                            return true;
                        }
                    }
                    return false;
                }
            case MEMBER_REFERENCE:
                // An inexact method reference expression.
                return b || !isExact((MemberReferenceTree) expressionTree);
            case PARENTHESIZED:
                // A parenthesized expression whose contained expression is not pertinent to
                // applicability.
                return notPertinentToApplicability(TreeUtils.skipParens(expressionTree), b);
            case CONDITIONAL_EXPRESSION:
                ConditionalExpressionTree conditional = (ConditionalExpressionTree) expressionTree;
                // A conditional expression whose second or third operand is not pertinent to
                // applicability.
                return notPertinentToApplicability(conditional.getTrueExpression(), b)
                        || notPertinentToApplicability(conditional.getTrueExpression(), b);
            default:
                return false;
        }
    }

    public static boolean isPolyExpression(ExpressionTree expression) {
        if (expression instanceof com.sun.tools.javac.tree.JCTree.JCExpression) {
            return ((com.sun.tools.javac.tree.JCTree.JCExpression) expression).isPoly();
        }
        return false;
    }

    /**
     * Returns the parameter types of the potentially applicable method.
     *
     * <p>See https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.2.1
     *
     * @param ref
     * @return
     */
    public static List<? extends TypeMirror> getParametersOfPAMethod(MemberReferenceTree ref) {
        ExecutableElement ele = (ExecutableElement) TreeUtils.elementFromTree(ref);
        List<TypeMirror> params = new ArrayList<>();
        for (VariableElement var : ele.getParameters()) {
            params.add(var.asType());
        }
        return params;
    }

    public static boolean isStandaloneExpression(ExpressionTree expression) {

        if (expression instanceof com.sun.tools.javac.tree.JCTree.JCExpression) {
            return ((com.sun.tools.javac.tree.JCTree.JCExpression) expression).isStandalone();
        }
        return false;
    }

    public static TypeMirror findCommonParameterizedTypes(Type aBIG, Type bBIG, Types types) {
        if (TypesUtils.isObject(aBIG) || TypesUtils.isObject(bBIG)) {
            return null;
        }
        if (!aBIG.getTypeArguments().isEmpty() && !bBIG.getTypeArguments().isEmpty()) {
            Type aErased = types.erasure(aBIG);
            Type bErased = types.erasure(bBIG);
            if (types.isSameType(aErased, bErased)) {
                return aBIG;
            }
        }

        for (Type a : types.directSupertypes(aBIG)) {
            for (Type b : types.directSupertypes(bBIG)) {
                TypeMirror recur = findCommonParameterizedTypes(a, b, types);
                if (recur != null) {
                    return recur;
                }
            }
        }
        return null;
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
            receiverType = (DeclaredType) receiverType.getEnclosingType();
            if (receiverType == null) {
                ErrorReporter.errorAbort("Method not found");
            }
        }
        javax.lang.model.util.Types types = context.env.getTypeUtils();
        return (ExecutableType) types.asMemberOf(receiverType, ele);
    }

    public static boolean isRaw(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            TypeElement typeelem = (TypeElement) ((DeclaredType) type).asElement();
            DeclaredType declty = (DeclaredType) typeelem.asType();
            return !declty.getTypeArguments().isEmpty()
                    && ((DeclaredType) type).getTypeArguments().isEmpty();
        }
        return false;
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
        return context.types.freshTypeVariables(
                        com.sun.tools.javac.util.List.of((Type) wildcardType))
                .head;
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
}
