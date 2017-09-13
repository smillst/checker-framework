package org.checkerframework.framework.util.typeinference8.types;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;

public class Dependencies {
    private final Map<Variable, SortedSet<Variable>> map = new LinkedHashMap<>();

    public boolean putOrAdd(Variable key, Variable value) {
        SortedSet<Variable> set = map.get(key);
        if (set == null) {
            set = new TreeSet<>();
            map.put(key, set);
        }
        return set.add(value);
    }

    public boolean putOrAddAll(Variable key, SortedSet<Variable> value) {
        SortedSet<Variable> set = map.get(key);
        if (set == null) {
            set = new TreeSet<>();
            map.put(key, set);
        }
        return set.addAll(value);
    }

    public void addTransitive() {
        // An inference variable alpha depends on the resolution of an inference variable beta if
        // there exists an inference variable gamma such that alpha depends on the resolution of
        // gamma and gamma depends on the resolution of beta.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Entry<Variable, SortedSet<Variable>> entry : map.entrySet()) {
                Variable alpha = entry.getKey();
                SortedSet<Variable> gammas = entry.getValue();
                SortedSet<Variable> betas = new TreeSet<>();
                for (Variable gamma : gammas) {
                    if (gamma.equals(alpha)) {
                        continue;
                    }
                    betas.addAll(map.get(gamma));
                }
                changed |= gammas.addAll(betas);
            }
        }
    }

    public SortedSet<Variable> get(Variable alpha) {
        return map.get(alpha);
    }

    public SortedSet<Variable> get(List<Variable> variables) {
        SortedSet<Variable> set = new TreeSet<>();
        for (Variable v : variables) {
            set.addAll(get(v));
        }
        return set;
    }
}
