package io.github.connellite.stoneorm.jdbc;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.reflection.ReflectionUtil;
import io.github.connellite.stoneorm.StoneOrmException;
import io.github.connellite.stoneorm.mapping.EntityField;
import io.github.connellite.stoneorm.mapping.EntityModel;
import io.github.connellite.util.NumberUtils;
import io.github.connellite.util.UuidUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

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
        Object coerced = coerce(value, f.javaType());
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
        T entity = newInstance(model);
        for (EntityField f : model.fields()) {
            String col = f.columnName();
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

    private static Object coerce(Object value, Class<?> target) {
        if (value == null) {
            if (target.isPrimitive()) {
                throw new StoneOrmException("Cannot set null on primitive: " + target);
            }
            return null;
        }
        if (target.isInstance(value)) {
            return value;
        }
        Class<?> boxed = ReflectionUtil.primitiveToWrapper(target);
        if (Number.class.isAssignableFrom(boxed) && value instanceof Number n) {
            return NumberUtils.narrowNumber(n, boxed);
        }
        if (boxed == Boolean.class) {
            if (value instanceof Boolean b) {
                return b;
            }
            if (value instanceof Number num) {
                return NumberUtils.toBoolean(num.longValue());
            }
            throw new StoneOrmException("Unsupported conversion to boolean from " + value.getClass());
        }
        if (boxed == UUID.class) {
            return UuidUtil.convert2Uuid(value);
        }
        if (boxed == String.class) {
            return value.toString();
        }
        throw new StoneOrmException("Unsupported conversion to " + target.getName() + " from " + value.getClass());
    }
}
