package io.github.connellite.microorm.mapping;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.microorm.MicroOrmException;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/** One mapped column ↔ field. */
public final class EntityField {

    private final Field javaField;
    private final VarHandle varHandle;
    private final String columnName;
    private final boolean id;
    private final boolean autoIncrement;
    private final boolean nullable;
    private final boolean unique;
    private final boolean indexed;
    private final String sqlType;
    private final int length;

    public EntityField(Field javaField, String columnName, boolean id, boolean autoIncrement, boolean nullable) {
        this(javaField, columnName, id, autoIncrement, nullable, false, false, "", 0);
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
        this.javaField = javaField;
        this.columnName = columnName;
        this.id = id;
        this.autoIncrement = autoIncrement;
        this.nullable = nullable;
        this.unique = unique;
        this.indexed = indexed;
        this.sqlType = sqlType == null ? "" : sqlType;
        this.length = length;
        try {
            this.varHandle = MethodHandleReflectionUtil.varHandle(javaField);
        } catch (IllegalAccessException e) {
            throw new MicroOrmException("Cannot access field " + javaField.getName(), e);
        }
    }

    public Field javaField() {
        return javaField;
    }

    public VarHandle varHandle() {
        return varHandle;
    }

    public String columnName() {
        return columnName;
    }

    public boolean id() {
        return id;
    }

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

    public Class<?> javaType() {
        return javaField.getType();
    }
}
