package io.github.connellite.microorm.mapping;

import java.util.List;

/** Immutable metadata for a table unique constraint. */
public record TableUniqueConstraint(String name, List<String> columnNames) {
    public TableUniqueConstraint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("unique constraint name cannot be blank");
        }
        columnNames = List.copyOf(columnNames);
        if (columnNames.isEmpty()) {
            throw new IllegalArgumentException("unique constraint column list cannot be empty");
        }
    }
}
