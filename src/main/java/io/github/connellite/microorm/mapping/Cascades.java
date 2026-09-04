package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.annotation.CascadeType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

final class Cascades {

    private Cascades() {
    }

    static Set<CascadeType> copyCascade(CascadeType[] cascade) {
        if (cascade == null || cascade.length == 0) {
            return Set.of();
        }
        EnumSet<CascadeType> copied = EnumSet.noneOf(CascadeType.class);
        Collections.addAll(copied, cascade);
        return Set.copyOf(copied);
    }

    static boolean enabled(Set<CascadeType> cascade, CascadeType type) {
        return cascade.contains(CascadeType.ALL) || cascade.contains(type);
    }
}
