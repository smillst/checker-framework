package org.checkerframework.framework.util.typeinference8.types;

import com.sun.source.tree.ExpressionTree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.util.typeinference8.util.Context;

public class Theta extends HashMap<TypeVariable, Variable> {
    private static final long serialVersionUID = 42L;
    List<Entry<TypeVariable, Variable>> entryList = new ArrayList<>();

    @Override
    public Variable put(TypeVariable key, Variable value) {
        assert !this.containsKey(key) || this.get(key).equals(value);
        if (!this.containsKey(key)) {
            entryList.add(new SimpleEntry<>(key, value));
        }
        return super.put(key, value);
    }

    public List<Entry<TypeVariable, Variable>> getEntryList() {
        return entryList;
    }

    @Override
    public Set<Entry<TypeVariable, Variable>> entrySet() {
        return super.entrySet();
    }

    @Override
    public Set<TypeVariable> keySet() {
        return super.keySet();
    }

    public static Theta theta(ExpressionTree tree, ExecutableType methodType, Context context) {
        if (context.maps.containsKey(tree)) {
            return context.maps.get(tree);
        }
        Theta map = new Theta();
        for (TypeVariable pl : methodType.getTypeVariables()) {
            Variable al = new Variable(pl, tree, context);
            map.put(pl, al);
        }
        for (Variable v : map.values()) {
            v.initalBounds(map);
        }
        context.maps.put(tree, map);
        return map;
    }
}
