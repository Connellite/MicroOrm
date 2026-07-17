package io.github.connellite.microorm.jdbc;

import io.github.connellite.exception.TypeCoercionException;
import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.reflection.ReflectionUtil;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.mapping.LifecycleCallbacks;
import io.github.connellite.microorm.mapping.LifecycleEvent;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.OneToManyField;
import io.github.connellite.microorm.relation.EagerCollection;
import io.github.connellite.microorm.relation.EagerRef;
import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.relation.LazyLoadContext;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.sql.SqlIdentifier;
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
        return isUnsetPkValue(getFieldValue(entity, pk), pk);
    }

    public static boolean isUnsetPkValue(Object value, EntityField pk) {
        if (value == null) {
            return true;
        }
        if (pk.autoIncrement() && value instanceof Number n) {
            return n.longValue() == 0L;
        }
        return false;
    }

    public static void requirePkValue(Object value, EntityField pk) {
        if (isUnsetPkValue(value, pk)) {
            throw new MicroOrmException("Primary key value is required for column '" + pk.columnName() + "'");
        }
    }

    public static void requirePkSet(Object entity, EntityField pk) {
        requirePkValue(getFieldValue(entity, pk), pk);
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
        return mapRow(model, rs, null, null, null, null, null);
    }

    public static <T> T mapRow(
            EntityModel model,
            ResultSet rs,
            Collection<String> availableColumns,
            JdbcValueMapper valueMapper) throws SQLException {
        return mapRow(model, rs, availableColumns, valueMapper, null, null, null);
    }

    public static <T> T mapRow(
            EntityModel model,
            ResultSet rs,
            Collection<String> availableColumns,
            JdbcValueMapper valueMapper,
            Dialect dialect,
            LazyLoadContext lazyContext,
            EntityModelRegistry registry) throws SQLException {
        T entity = newInstance(model);
        for (EntityField f : model.fields()) {
            if (!hasColumn(availableColumns, dialect, f.columnIdentifier())) {
                continue;
            }
            Object raw = readColumn(rs, dialect, f.columnIdentifier(), f, valueMapper);
            if (raw == null || rs.wasNull()) {
                setFieldValue(entity, f, null);
            } else {
                setFieldValue(entity, f, valueMapper == null ? raw : valueMapper.fromJdbcValue(f, raw));
            }
        }
        if (lazyContext != null && registry != null && dialect != null) {
            attachRelations(entity, model, rs, availableColumns, valueMapper, dialect, lazyContext, registry);
        }
        LifecycleCallbacks.invoke(entity, LifecycleEvent.POST_LOAD);
        return entity;
    }

    private static void attachRelations(
            Object entity,
            EntityModel model,
            ResultSet rs,
            Collection<String> availableColumns,
            JdbcValueMapper valueMapper,
            Dialect dialect,
            LazyLoadContext lazyContext,
            EntityModelRegistry registry) throws SQLException {
        Object ownerId = getFieldValue(entity, model.primaryKey());
        for (ManyToOneField relation : model.manyToOneRelations()) {
            if (!hasColumn(availableColumns, dialect, relation.joinColumnIdentifier())) {
                continue;
            }
            EntityModel targetModel = registry.get(relation.targetEntityClass());
            EntityField targetPk = targetModel.primaryKey();
            Object raw = readColumn(rs, dialect, relation.joinColumnIdentifier(), targetPk, valueMapper);
            Object foreignKey = null;
            if (raw != null && !rs.wasNull()) {
                foreignKey = coerceToTargetPk(raw, targetPk, valueMapper);
            }
            if (EagerRef.class.isAssignableFrom(relation.javaField().getType())) {
                Object target = foreignKey == null ? null : lazyContext.loadById(relation.targetEntityClass(), foreignKey);
                EagerRef.set(relation, entity, EagerRef.of(relation.targetEntityClass(), foreignKey, target));
            } else {
                LazyRef<?> lazyRef = LazyRef.of(lazyContext, relation.targetEntityClass(), foreignKey);
                LazyRef.set(relation, entity, lazyRef);
            }
        }
        for (OneToManyField relation : model.oneToManyRelations()) {
            if (EagerCollection.class.isAssignableFrom(relation.javaField().getType())) {
                EagerCollection<?> collection = EagerCollection.of(ownerId, lazyContext.loadCollection(relation, ownerId));
                EagerCollection.set(relation, entity, collection);
            } else {
                LazyCollection<?> collection = LazyCollection.of(lazyContext, relation, ownerId);
                LazyCollection.set(relation, entity, collection);
            }
        }
    }

    private static Object readColumn(
            ResultSet rs,
            Dialect dialect,
            SqlIdentifier identifier,
            EntityField field,
            JdbcValueMapper valueMapper) throws SQLException {
        String label = dialect == null ? identifier.text() : dialect.jdbcColumnLabel(identifier);
        if (valueMapper == null) {
            return rs.getObject(label);
        }
        return valueMapper.readJdbcValue(field, rs, label);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean hasColumn(Collection<String> availableColumns, Dialect dialect, SqlIdentifier identifier) {
        if (availableColumns == null) {
            return true;
        }
        String label = dialect == null ? identifier.text() : dialect.jdbcColumnLabel(identifier);
        for (String column : availableColumns) {
            if (column.equals(label) || column.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    private static Object coerceToTargetPk(Object raw, EntityField targetPk, JdbcValueMapper valueMapper) {
        Object jdbcValue = valueMapper == null ? raw : valueMapper.fromJdbcValue(targetPk, raw);
        return coerce(jdbcValue, targetPk);
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
