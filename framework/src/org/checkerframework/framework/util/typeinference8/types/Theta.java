package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import java.util.HashMap;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.util.Context;

public class Theta extends HashMap<TypeVariable, Variable> {
    private static final long serialVersionUID = 42L;

    @Override
    public Variable put(TypeVariable key, Variable value) {
        assert !this.containsKey(key) || this.get(key).equals(value);
        return super.put(key, value);
    }

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
}
