package io.github.connellite.microorm.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Fluent SQL-oriented INSERT builder for one mapped entity table. */
public final class EntityInsert<T> {

    private final Class<T> entityType;
    private final Map<String, Object> values = new LinkedHashMap<>();

    private EntityInsert(Class<T> entityType) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    /** Starts an INSERT for the given entity type. */
    public static <T> EntityInsert<T> into(Class<T> entityType) {
        return new EntityInsert<>(entityType);
    }

    public Class<T> entityType() {
        return entityType;
    }

    public Map<String, Object> values() {
        return Collections.unmodifiableMap(values);
    }

    public EntityInsert<T> value(String fieldName, Object value) {
        values.put(requireFieldName(fieldName), value);
        return this;
    }

    public EntityInsert<T> value(FieldPath field, Object value) {
        Objects.requireNonNull(field, "field");
        return value(field.name(), value);
    }

    public <R> EntityInsert<T> value(EntitySelect.Getter<T, R> getter, Object value) {
        return value(EntitySelect.field(getter), value);
    }

    public EntityInsert<T> value(EntitySelect.Attribute<T, ?> attribute, Object value) {
        return value(EntitySelect.field(attribute), value);
    }

    public EntityInsert<T> values(Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        values.forEach(this::value);
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
