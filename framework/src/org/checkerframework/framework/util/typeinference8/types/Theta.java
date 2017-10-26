package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.util.Context;

public class Theta extends HashMap<TypeVariable, Variable> {
    private static Map<ExpressionTree, Theta> maps = new HashMap<>();
    private static final long serialVersionUID = 42L;

    @Override
    public Variable put(TypeVariable key, Variable value) {
        assert !this.containsKey(key) || this.get(key).equals(value);
        return super.put(key, value);
    }

    public static Theta theta(ExpressionTree tree, ExecutableType methodType, Context context) {
        if (maps.containsKey(tree)) {
            return maps.get(tree);
        }
        Theta map = new Theta();
        for (TypeVariable pl : methodType.getTypeVariables()) {
            Variable al = new Variable(pl, tree, context);
            map.put(pl, al);
        }
        maps.put(tree, map);
        return map;
    }
}
