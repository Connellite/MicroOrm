package io.github.connellite.microorm.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Fluent SQL-oriented UPDATE builder for one mapped entity table. */
public final class EntityUpdate<T> {

    private final Class<T> entityType;
    private final Map<String, Object> assignments = new LinkedHashMap<>();
    private Criterion criterion;
    private boolean allRows;

    private EntityUpdate(Class<T> entityType) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    /** Starts an UPDATE for the given entity type. */
    public static <T> EntityUpdate<T> of(Class<T> entityType) {
        return new EntityUpdate<>(entityType);
    }

    public Class<T> entityType() {
        return entityType;
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

    public EntityUpdate<T> set(String fieldName, Object value) {
        assignments.put(requireFieldName(fieldName), value);
        return this;
    }

    public EntityUpdate<T> set(FieldPath field, Object value) {
        Objects.requireNonNull(field, "field");
        return set(field.name(), value);
    }

    public <R> EntityUpdate<T> set(EntitySelect.Getter<T, R> getter, Object value) {
        return set(EntitySelect.field(getter), value);
    }

    public EntityUpdate<T> set(EntitySelect.Attribute<T, ?> attribute, Object value) {
        return set(EntitySelect.field(attribute), value);
    }

    public EntityUpdate<T> set(Map<String, ?> assignments) {
        Objects.requireNonNull(assignments, "assignments");
        assignments.forEach(this::set);
        return this;
    }

    public EntityUpdate<T> where(Criterion criterion) {
        this.criterion = Objects.requireNonNull(criterion, "criterion");
        this.allRows = false;
        return this;
    }

    public EntityUpdate<T> and(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.and(criterion);
        this.allRows = false;
        return this;
    }

    public EntityUpdate<T> or(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.or(criterion);
        this.allRows = false;
        return this;
    }

    /** Explicitly marks this UPDATE as intentionally affecting all rows. */
    public EntityUpdate<T> allRows() {
        this.criterion = null;
        this.allRows = true;
        return this;
    }

    private static String requireFieldName(String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        return fieldName;
    }
}
