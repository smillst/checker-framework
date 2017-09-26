package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.bound.Capture.CaptureTuple;
import org.checkerframework.framework.util.typeinference8.util.Context;
import org.checkerframework.javacutil.InternalUtils;

public class Theta extends HashMap<TypeVariable, Variable> {
    private static final long serialVersionUID = 42L;

    public static Theta theta(
            ExecutableElement methodElement, ExpressionTree tree, Context context) {
        ExecutableType type = (ExecutableType) methodElement.asType();

        Theta map = new Theta();
        for (TypeVariable pl : type.getTypeVariables()) {
            Variable al = new Variable(pl, tree, context);
            map.put(pl, al);
        }
        return map;
    }

    public static Theta theta(
            DeclaredType declaredType,
            Iterator<AbstractType> args,
            List<CaptureTuple> captureTuples) {
        TypeElement ele = InternalUtils.getTypeElement(declaredType);
        Theta map = new Theta();
        //        for (TypeParameterElement pEle : ele.getTypeParameters()) {
        //            TypeVariable pl = (TypeVariable) pEle.asType();
        //            Variable al = new Variable(pl, tree);
        //            map.put(pl, al);
        //            captureTuples.add(CaptureTuple.of(al, args.next(), new ProperType(pl.getUpperBound())));
        //        }
        return map;
    }
}
