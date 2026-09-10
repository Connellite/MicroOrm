package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.query.Criterion;
import io.github.connellite.microorm.query.FieldPath;

import java.util.Objects;

/** Fluent SQL-oriented DELETE builder for one registered dynamic table. */
public final class DynamicDelete {

    private final String tableName;
    private Criterion criterion;
    private boolean allRows;

    private DynamicDelete(String tableName) {
        this.tableName = requireName(tableName, "tableName");
    }

    /** Starts a DELETE for the registered dynamic table name. */
    public static DynamicDelete from(String tableName) {
        return new DynamicDelete(tableName);
    }

    /** Creates a column reference for criteria. */
    public static FieldPath field(String columnName) {
        return new FieldPath(columnName);
    }

    public String tableName() {
        return tableName;
    }

    public Criterion criterion() {
        return criterion;
    }

    public boolean isAllRows() {
        return allRows;
    }

    public DynamicDelete where(Criterion criterion) {
        this.criterion = Objects.requireNonNull(criterion, "criterion");
        this.allRows = false;
        return this;
    }

    public DynamicDelete and(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.and(criterion);
        this.allRows = false;
        return this;
    }

    public DynamicDelete or(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.or(criterion);
        this.allRows = false;
        return this;
    }

    /** Explicitly marks this DELETE as intentionally affecting all rows. */
    public DynamicDelete allRows() {
        this.criterion = null;
        this.allRows = true;
        return this;
    }

    private static String requireName(String name, String label) {
        Objects.requireNonNull(name, label);
        if (name.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return name;
    }
}
