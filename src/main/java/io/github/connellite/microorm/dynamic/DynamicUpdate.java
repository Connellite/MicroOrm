package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.query.Criterion;
import io.github.connellite.microorm.query.FieldPath;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Fluent SQL-oriented UPDATE builder for one registered dynamic table. */
public final class DynamicUpdate {

    private final String tableName;
    private final Map<String, Object> assignments = new LinkedHashMap<>();
    private Criterion criterion;
    private boolean allRows;

    private DynamicUpdate(String tableName) {
        this.tableName = requireName(tableName, "tableName");
    }

    /** Starts an UPDATE for the registered dynamic table name. */
    public static DynamicUpdate table(String tableName) {
        return new DynamicUpdate(tableName);
    }

    /** Creates a column reference for criteria. */
    public static FieldPath field(String columnName) {
        return new FieldPath(columnName);
    }

    public String tableName() {
        return tableName;
    }

    public Map<String, Object> assignments() {
        return Collections.unmodifiableMap(assignments);
    }

    public Criterion criterion() {
        return criterion;
    }

    public boolean isAllRows() {
        return allRows;
    }

    public DynamicUpdate set(String columnName, Object value) {
        assignments.put(requireName(columnName, "columnName"), value);
        return this;
    }

    public DynamicUpdate set(FieldPath field, Object value) {
        Objects.requireNonNull(field, "field");
        return set(field.name(), value);
    }

    public DynamicUpdate set(Map<String, ?> assignments) {
        Objects.requireNonNull(assignments, "assignments");
        assignments.forEach(this::set);
        return this;
    }

    public DynamicUpdate where(Criterion criterion) {
        this.criterion = Objects.requireNonNull(criterion, "criterion");
        this.allRows = false;
        return this;
    }

    public DynamicUpdate and(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.and(criterion);
        this.allRows = false;
        return this;
    }

    public DynamicUpdate or(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.or(criterion);
        this.allRows = false;
        return this;
    }

    /** Explicitly marks this UPDATE as intentionally affecting all rows. */
    public DynamicUpdate allRows() {
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
