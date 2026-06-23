package io.github.connellite.stoneorm.mapping;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.stoneorm.StoneOrmException;

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

    public EntityField(Field javaField, String columnName, boolean id, boolean autoIncrement, boolean nullable) {
        this.javaField = javaField;
        this.columnName = columnName;
        this.id = id;
        this.autoIncrement = autoIncrement;
        this.nullable = nullable;
        try {
            this.varHandle = MethodHandleReflectionUtil.varHandle(javaField);
        } catch (IllegalAccessException e) {
            throw new StoneOrmException("Cannot access field " + javaField.getName(), e);
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

    public Class<?> javaType() {
        return javaField.getType();
    }
}
