package io.github.connellite.microorm.mapping;

import java.util.List;

/** Immutable metadata for a table index. */
public record TableIndex(String name, List<String> columnList, boolean unique) {
    public TableIndex {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("index name cannot be blank");
        }
        columnList = List.copyOf(columnList);
        if (columnList.isEmpty()) {
            throw new IllegalArgumentException("index column list cannot be empty");
        }
    }
}
