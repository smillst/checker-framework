package org.checkerframework.framework.util.typeinference8.util;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.TreeScanner;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.UnionType;
import org.checkerframework.javacutil.TreeUtils;

public class CheckedExceptionsUtil {

    public static List<TypeMirror> thrownCheckedExceptions(Tree tree, Context context) {
        return new CheckedExceptionVisitor(context).scan(tree, null);
    }

    private static class CheckedExceptionVisitor extends TreeScanner<List<TypeMirror>, Void> {

        private Context context;

        protected CheckedExceptionVisitor(Context context) {
            this.context = context;
        }

        @Override
        public List<TypeMirror> reduce(List<TypeMirror> r1, List<TypeMirror> r2) {
            if (r1 == null) {
                return r2;
            }
            if (r2 == null) {
                return r1;
            }
            r1.addAll(r2);
            return r1;
        }

        @Override
        public List<TypeMirror> visitThrow(ThrowTree node, Void aVoid) {
            List<TypeMirror> result = super.visitThrow(node, aVoid);
            if (result == null) {
                result = new ArrayList<>();
            }
            TypeMirror type = TreeUtils.typeOf(node);
            if (isCheckedException(type, context)) {
                result.add(type);
            }
            return result;
        }

        @Override
        public List<TypeMirror> visitMethodInvocation(MethodInvocationTree node, Void aVoid) {
            List<TypeMirror> result = super.visitMethodInvocation(node, aVoid);
            if (result == null) {
                result = new ArrayList<>();
            }
            for (TypeMirror type : TreeUtils.elementFromUse(node).getThrownTypes()) {
                if (isCheckedException(type, context)) {
                    result.add(type);
                }
            }
            return result;
        }

        @Override
        public List<TypeMirror> visitNewClass(NewClassTree node, Void aVoid) {
            List<TypeMirror> result = super.visitNewClass(node, aVoid);
            if (result == null) {
                result = new ArrayList<>();
            }
            for (TypeMirror type : TreeUtils.elementFromUse(node).getThrownTypes()) {
                if (isCheckedException(type, context)) {
                    result.add(type);
                }
            }
            return result;
        }

        @Override
        public List<TypeMirror> visitTry(TryTree node, Void aVoid) {
            List<TypeMirror> results = scan(node.getBlock(), aVoid);
            if (results == null) {
                results = new ArrayList<>();
            }

            if (!results.isEmpty()) {
                for (CatchTree catchTree : node.getCatches()) {
                    removeSame(TreeUtils.typeOf(catchTree.getParameter()), results);
                }
            }
            results.addAll(scan(node.getResources(), aVoid));
            results.addAll(scan(node.getCatches(), aVoid));
            results.addAll(scan(node.getFinallyBlock(), aVoid));

            return results;
        }

        private void removeSame(TypeMirror type, List<TypeMirror> thrownExceptionTypes) {
            if (thrownExceptionTypes.isEmpty()) {
                return;
            }
            if (type.getKind() == TypeKind.UNION) {
                for (TypeMirror altern : ((UnionType) type).getAlternatives()) {
                    removeSame(altern, thrownExceptionTypes);
                }
            } else {
                for (TypeMirror thrownType : new ArrayList<>(thrownExceptionTypes)) {
                    if (context.env.getTypeUtils().isSameType(thrownType, type)) {
                        thrownExceptionTypes.remove(thrownType);
                    }
                }
            }
        }
    }

    public static boolean isCheckedException(TypeMirror t, Context context) {
        TypeMirror runtimeEx = context.runtimeEx;
        return context.env.getTypeUtils().isSubtype(t, runtimeEx);
    }
}
