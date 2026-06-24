package io.github.connellite.microorm.jdbc;

import io.github.connellite.exception.TypeCoercionException;
import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.reflection.ReflectionUtil;
import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.util.TypeCoercionUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

/** Maps {@link java.sql.ResultSet} rows to entity instances and reads/writes field values. */
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
            throw new MicroOrmException("Cannot map SQL NULL to primitive field '"
                    + f.javaField().getName() + "' on " + entity.getClass().getName());
        }
        Object coerced = coerce(value, f);
        MethodHandleReflectionUtil.set(f.varHandle(), f.javaField(), entity, coerced);
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(EntityModel model) {
        try {
            return (T) ReflectionUtil.getInstance(model.entityClass());
        } catch (ReflectiveOperationException e) {
            throw new MicroOrmException("Cannot instantiate " + model.entityClass().getName(), e);
        }
    }

    public static <T> T mapRow(EntityModel model, ResultSet rs) throws SQLException {
        return mapRow(model, rs, null, null);
    }

    public static <T> T mapRow(
            EntityModel model,
            ResultSet rs,
            Collection<String> availableColumns,
            JdbcValueMapper valueMapper) throws SQLException {
        T entity = newInstance(model);
        for (EntityField f : model.fields()) {
            String col = f.columnName();
            if (availableColumns != null && !availableColumns.contains(col)) {
                continue;
            }
            Object raw = rs.getObject(col);
            if (raw == null || rs.wasNull()) {
                setFieldValue(entity, f, null);
            } else {
                setFieldValue(entity, f, valueMapper == null ? raw : valueMapper.fromJdbcValue(f, raw));
            }
        }
        return entity;
    }

    private static Object coerce(Object value, EntityField field) {
        try {
            return TypeCoercionUtil.coerce(value, field.javaType());
        } catch (TypeCoercionException e) {
            throw new MicroOrmException("Cannot map column '" + field.columnName()
                    + "' to " + field.javaType().getName(), e);
        }
    }
}
