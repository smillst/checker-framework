package org.checkerframework.framework.util.typeinference8.types;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Dependencies {
    private final Map<Variable, LinkedHashSet<Variable>> map = new LinkedHashMap<>();

    public boolean putOrAdd(Variable key, Variable value) {
        LinkedHashSet<Variable> set = map.get(key);
        if (set == null) {
            set = new LinkedHashSet<>();
            map.put(key, set);
        }
        return set.add(value);
    }

    public boolean putOrAddAll(Variable key, LinkedHashSet<Variable> value) {
        LinkedHashSet<Variable> set = map.get(key);
        if (set == null) {
            set = new LinkedHashSet<>();
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
            for (Entry<Variable, LinkedHashSet<Variable>> entry : map.entrySet()) {
                Variable alpha = entry.getKey();
                LinkedHashSet<Variable> gammas = entry.getValue();
                LinkedHashSet<Variable> betas = new LinkedHashSet<>();
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

    public LinkedHashSet<Variable> get(Variable alpha) {
        return map.get(alpha);
    }

    public LinkedHashSet<Variable> get(List<Variable> variables) {
        LinkedHashSet<Variable> set = new LinkedHashSet<>();
        for (Variable v : variables) {
            LinkedHashSet<Variable> get = get(v);
            set.addAll(get);
        }
        return set;
    }
}
