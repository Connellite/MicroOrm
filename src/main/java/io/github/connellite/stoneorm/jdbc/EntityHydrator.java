package io.github.connellite.stoneorm.jdbc;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.reflection.ReflectionUtil;
import io.github.connellite.exception.TypeCoercionException;
import io.github.connellite.stoneorm.StoneOrmException;
import io.github.connellite.stoneorm.mapping.EntityField;
import io.github.connellite.stoneorm.mapping.EntityModel;
import io.github.connellite.util.TypeCoercionUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public final class EntityHydrator {

    private EntityHydrator() {
    }

    public static boolean isUnsetPk(Object entity, EntityField pk) {
        Object v = getFieldValue(entity, pk);
        if (v == null) {
            return true;
        }
        if (v instanceof Number n) {
            return n.longValue() == 0L;
        }
        return false;
    }

    public static Object getFieldValue(Object entity, EntityField f) {
        return MethodHandleReflectionUtil.get(f.varHandle(), entity);
    }

    public static void setFieldValue(Object entity, EntityField f, Object value) {
        if (value == null && f.javaType().isPrimitive()) {
            return;
        }
        Object coerced = coerce(value, f);
        MethodHandleReflectionUtil.set(f.varHandle(), f.javaField(), entity, coerced);
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(EntityModel model) {
        try {
            return (T) ReflectionUtil.getInstance(model.entityClass());
        } catch (ReflectiveOperationException e) {
            throw new StoneOrmException("Cannot instantiate " + model.entityClass().getName(), e);
        }
    }

    public static <T> T mapRow(EntityModel model, ResultSet rs) throws SQLException {
        return mapRow(model, rs, null);
    }

    public static <T> T mapRow(EntityModel model, ResultSet rs, Collection<String> availableColumns) throws SQLException {
        T entity = newInstance(model);
        for (EntityField f : model.fields()) {
            String col = f.columnName();
            if (availableColumns != null && !availableColumns.contains(col)) {
                continue;
            }
            Object raw = rs.getObject(col);
            if (raw == null || rs.wasNull()) {
                if (!f.javaType().isPrimitive()) {
                    setFieldValue(entity, f, null);
                }
            } else {
                setFieldValue(entity, f, raw);
            }
        }
        return entity;
    }

    private static Object coerce(Object value, EntityField field) {
        try {
            return TypeCoercionUtil.coerce(value, field.javaType());
        } catch (TypeCoercionException e) {
            throw new StoneOrmException("Cannot map column '" + field.columnName()
                    + "' to " + field.javaType().getName(), e);
        }
    }
}
