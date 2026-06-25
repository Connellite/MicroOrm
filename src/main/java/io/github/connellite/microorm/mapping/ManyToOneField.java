package io.github.connellite.microorm.mapping;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.microorm.MicroOrmException;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

import io.github.connellite.microorm.sql.SqlIdentifier;

/** Metadata for a {@link io.github.connellite.microorm.annotation.ManyToOne} {@link io.github.connellite.microorm.relation.LazyRef} field. */
public final class ManyToOneField {

    private final Field javaField;
    private final VarHandle varHandle;
    private final Class<?> targetEntityClass;
    private final SqlIdentifier joinColumnIdentifier;
    private final boolean nullable;
    private final Class<?> foreignKeyJavaType;

    public ManyToOneField(
            Field javaField,
            Class<?> targetEntityClass,
            String joinColumn,
            boolean nullable,
            Class<?> foreignKeyJavaType) {
        this(javaField, targetEntityClass, SqlIdentifier.unquoted(joinColumn), nullable, foreignKeyJavaType);
    }

    public ManyToOneField(
            Field javaField,
            Class<?> targetEntityClass,
            SqlIdentifier joinColumnIdentifier,
            boolean nullable,
            Class<?> foreignKeyJavaType) {
        this.javaField = javaField;
        this.targetEntityClass = targetEntityClass;
        this.joinColumnIdentifier = joinColumnIdentifier;
        this.nullable = nullable;
        this.foreignKeyJavaType = foreignKeyJavaType;
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

    public Class<?> targetEntityClass() {
        return targetEntityClass;
    }

    public String joinColumn() {
        return joinColumnIdentifier.text();
    }

    public SqlIdentifier joinColumnIdentifier() {
        return joinColumnIdentifier;
    }

    public boolean nullable() {
        return nullable;
    }

    public Class<?> foreignKeyJavaType() {
        return foreignKeyJavaType;
    }
}
