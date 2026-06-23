package io.github.connellite.stoneorm.jdbc;

import io.github.connellite.stoneorm.StoneOrmException;
import io.github.connellite.stoneorm.mapping.EntityField;
import io.github.connellite.stoneorm.mapping.EntityModel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
        try {
            return f.javaField().get(entity);
        } catch (IllegalAccessException e) {
            throw new StoneOrmException("Cannot read field " + f.javaField().getName(), e);
        }
    }

    public static void setFieldValue(Object entity, EntityField f, Object value) {
        try {
            Field jf = f.javaField();
            if (value == null && jf.getType().isPrimitive()) {
                return;
            }
            Object coerced = coerce(value, jf.getType());
            jf.set(entity, coerced);
        } catch (IllegalAccessException e) {
            throw new StoneOrmException("Cannot write field " + f.javaField().getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(EntityModel model) {
        try {
            Constructor<?> ctor = model.entityClass().getDeclaredConstructor();
            ctor.trySetAccessible();
            return (T) ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new StoneOrmException("Cannot instantiate " + model.entityClass().getName(), e);
        }
    }

    public static <T> T mapRow(EntityModel model, ResultSet rs) throws SQLException {
        T entity = newInstance(model);
        for (EntityField f : model.fields()) {
            String col = f.columnName();
            Class<?> t = f.javaType();
            if (t == long.class) {
                long v = rs.getLong(col);
                if (!rs.wasNull()) {
                    setFieldValue(entity, f, v);
                }
            } else if (t == int.class) {
                int v = rs.getInt(col);
                if (!rs.wasNull()) {
                    setFieldValue(entity, f, v);
                }
            } else if (t == short.class) {
                short v = rs.getShort(col);
                if (!rs.wasNull()) {
                    setFieldValue(entity, f, v);
                }
            } else if (t == byte.class) {
                byte v = rs.getByte(col);
                if (!rs.wasNull()) {
                    setFieldValue(entity, f, v);
                }
            } else if (t == boolean.class) {
                boolean v = rs.getBoolean(col);
                if (!rs.wasNull()) {
                    setFieldValue(entity, f, v);
                }
            } else if (t == double.class) {
                double v = rs.getDouble(col);
                if (!rs.wasNull()) {
                    setFieldValue(entity, f, v);
                }
            } else if (t == float.class) {
                float v = rs.getFloat(col);
                if (!rs.wasNull()) {
                    setFieldValue(entity, f, v);
                }
            } else {
                Object raw = rs.getObject(col);
                if (raw == null || rs.wasNull()) {
                    setFieldValue(entity, f, null);
                } else {
                    setFieldValue(entity, f, raw);
                }
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
        if (target == long.class || target == Long.class) {
            return ((Number) value).longValue();
        }
        if (target == int.class || target == Integer.class) {
            return ((Number) value).intValue();
        }
        if (target == short.class || target == Short.class) {
            return ((Number) value).shortValue();
        }
        if (target == byte.class || target == Byte.class) {
            return ((Number) value).byteValue();
        }
        if (target == boolean.class || target == Boolean.class) {
            if (value instanceof Boolean b) {
                return b;
            }
            return ((Number) value).intValue() != 0;
        }
        if (target == double.class || target == Double.class) {
            return ((Number) value).doubleValue();
        }
        if (target == float.class || target == Float.class) {
            return ((Number) value).floatValue();
        }
        if (target == UUID.class) {
            if (value instanceof UUID u) {
                return u;
            }
            return UUID.fromString(value.toString());
        }
        if (target == String.class) {
            return value.toString();
        }
        throw new StoneOrmException("Unsupported conversion to " + target.getName() + " from " + value.getClass());
    }
}
