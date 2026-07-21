package io.github.connellite.microorm.mapping;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.microorm.exception.MicroOrmException;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.AttributeConverter;

/** One mapped scalar column and its Java field (VarHandle-backed access). */
public final class EntityField {

    private final Field javaField;
    private final VarHandle varHandle;
    private final SqlIdentifier columnIdentifier;
    private final boolean id;
    private final boolean autoIncrement;
    private final boolean nullable;
    private final boolean unique;
    private final boolean indexed;
    private final String sqlType;
    private final int length;
    private final AttributeConverter<Object, Object> converter;
    private final Class<?> converterAttributeType;
    private final Class<?> converterDatabaseType;

    public EntityField(Field javaField, String columnName, boolean id, boolean autoIncrement, boolean nullable) {
        this(javaField, SqlIdentifier.unquoted(columnName), id, autoIncrement, nullable, false, false, "", 0);
    }

    public EntityField(Field javaField, SqlIdentifier columnIdentifier, boolean id, boolean autoIncrement, boolean nullable) {
        this(javaField, columnIdentifier, id, autoIncrement, nullable, false, false, "", 0);
    }

    public EntityField(
            Field javaField,
            String columnName,
            boolean id,
            boolean autoIncrement,
            boolean nullable,
            boolean unique,
            boolean indexed,
            String sqlType,
            int length) {
        this(javaField, SqlIdentifier.unquoted(columnName), id, autoIncrement, nullable, unique, indexed, sqlType, length);
    }

    public EntityField(
            Field javaField,
            SqlIdentifier columnIdentifier,
            boolean id,
            boolean autoIncrement,
            boolean nullable,
            boolean unique,
            boolean indexed,
            String sqlType,
            int length,
            AttributeConverter<?, ?> converter,
            Class<?> converterAttributeType,
            Class<?> converterDatabaseType) {
        this.javaField = javaField;
        this.columnIdentifier = columnIdentifier;
        this.id = id;
        this.autoIncrement = autoIncrement;
        this.nullable = nullable;
        this.unique = unique;
        this.indexed = indexed;
        this.sqlType = sqlType == null ? "" : sqlType;
        this.length = length;
        @SuppressWarnings("unchecked")
        AttributeConverter<Object, Object> typedConverter = (AttributeConverter<Object, Object>) converter;
        this.converter = typedConverter;
        this.converterAttributeType = converterAttributeType;
        this.converterDatabaseType = converterDatabaseType;
        try {
            this.varHandle = MethodHandleReflectionUtil.varHandle(javaField);
        } catch (IllegalAccessException e) {
            throw new MicroOrmException("Cannot access field " + javaField.getName(), e);
        }
    }

    public EntityField(
            Field javaField,
            SqlIdentifier columnIdentifier,
            boolean id,
            boolean autoIncrement,
            boolean nullable,
            boolean unique,
            boolean indexed,
            String sqlType,
            int length) {
        this.javaField = javaField;
        this.columnIdentifier = columnIdentifier;
        this.id = id;
        this.autoIncrement = autoIncrement;
        this.nullable = nullable;
        this.unique = unique;
        this.indexed = indexed;
        this.sqlType = sqlType == null ? "" : sqlType;
        this.length = length;
        this.converter = null;
        this.converterAttributeType = null;
        this.converterDatabaseType = null;
        try {
            this.varHandle = MethodHandleReflectionUtil.varHandle(javaField);
        } catch (IllegalAccessException e) {
            throw new MicroOrmException("Cannot access field " + javaField.getName(), e);
        }
    }

    /** Declared Java field on the entity class. */
    public Field javaField() {
        return javaField;
    }

    /** VarHandle for fast get/set on the entity instance. */
    public VarHandle varHandle() {
        return varHandle;
    }

    /** Physical column name text (without SQL quoting). */
    public String columnName() {
        return columnIdentifier.text();
    }

    /** Column identifier including quoting hint for the active {@link io.github.connellite.microorm.dialect.Dialect}. */
    public SqlIdentifier columnIdentifier() {
        return columnIdentifier;
    }

    /** {@code true} for the {@link io.github.connellite.microorm.annotation.Id} field. */
    public boolean id() {
        return id;
    }

    /** {@code true} when the primary key is database-generated on insert. */
    public boolean autoIncrement() {
        return autoIncrement;
    }

    public boolean nullable() {
        return nullable;
    }

    public boolean unique() {
        return unique;
    }

    public boolean indexed() {
        return indexed;
    }

    public String sqlType() {
        return sqlType;
    }

    public int length() {
        return length;
    }

    /** Declared Java field type. */
    public Class<?> javaType() {
        return javaField.getType();
    }

    /** Java type stored in JDBC after applying {@link io.github.connellite.microorm.annotation.Convert}. */
    public Class<?> jdbcJavaType() {
        return converterDatabaseType == null ? javaType() : converterDatabaseType;
    }

    /** Returns whether this field has an attribute converter. */
    public boolean converted() {
        return converter != null;
    }

    /** Converter entity-side type, or {@code null} when not converted. */
    public Class<?> converterAttributeType() {
        return converterAttributeType;
    }

    /** Converter database-side type, or {@code null} when not converted. */
    public Class<?> converterDatabaseType() {
        return converterDatabaseType;
    }

    /** Converts an entity attribute value to its database-side Java value. */
    public Object convertToDatabaseColumn(Object value) {
        return converter == null ? value : converter.convertToDatabaseColumn(value);
    }

    /** Converts a database-side Java value to its entity attribute value. */
    public Object convertToEntityAttribute(Object value) {
        return converter == null ? value : converter.convertToEntityAttribute(value);
    }
}
