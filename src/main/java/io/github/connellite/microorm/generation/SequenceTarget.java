package io.github.connellite.microorm.generation;

import io.github.connellite.microorm.sql.SqlIdentifier;

import java.util.Objects;

/** Dialect-neutral target for sequence DDL and next-value SQL. */
public record SequenceTarget(
        SqlIdentifier schemaIdentifier,
        SqlIdentifier tableIdentifier,
        SqlIdentifier primaryKeyIdentifier,
        IdGeneration generation) {

    public SequenceTarget {
        tableIdentifier = Objects.requireNonNull(tableIdentifier, "tableIdentifier");
        primaryKeyIdentifier = Objects.requireNonNull(primaryKeyIdentifier, "primaryKeyIdentifier");
        generation = Objects.requireNonNull(generation, "generation");
    }

    public String tableName() {
        return tableIdentifier.text();
    }

    public String primaryKeyName() {
        return primaryKeyIdentifier.text();
    }
}
