package io.github.connellite.microorm.mapping;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.microorm.exception.MicroOrmException;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/** Metadata for a {@link io.github.connellite.microorm.annotation.OneToMany} collection wrapper field. */
public final class OneToManyField {

    private final Field javaField;
    private final VarHandle varHandle;
    private final Class<?> targetEntityClass;
    private final String mappedBy;

    public OneToManyField(Field javaField, Class<?> targetEntityClass, String mappedBy) {
        this.javaField = javaField;
        this.targetEntityClass = targetEntityClass;
        this.mappedBy = mappedBy;
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

    public String mappedBy() {
        return mappedBy;
    }
}
