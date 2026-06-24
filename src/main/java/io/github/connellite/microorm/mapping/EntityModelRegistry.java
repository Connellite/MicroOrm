package io.github.connellite.microorm.mapping;

import io.github.connellite.collections.ConcurrentReferenceHashMap;
import io.github.connellite.reflection.ReflectionUtil;
import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Transient;
import io.github.connellite.microorm.sql.SqlGenerator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry of entity metadata built from annotations. */
public final class EntityModelRegistry {

    private static final int CACHE_INITIAL_CAPACITY = 64;

    private static final Set<Class<?>> SUPPORTED_FIELD_TYPES = Set.of(
            long.class, Long.class,
            int.class, Integer.class,
            short.class, Short.class,
            byte.class, Byte.class,
            boolean.class, Boolean.class,
            float.class, Float.class,
            double.class, Double.class,
            String.class,
            UUID.class);

    private final Set<Class<?>> registered = ConcurrentHashMap.newKeySet();
    private final ConcurrentReferenceHashMap<Class<?>, EntityModel> cache =
            new ConcurrentReferenceHashMap<>(CACHE_INITIAL_CAPACITY, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** Returns metadata for a previously {@link #register(Class) registered} entity. */
    public EntityModel get(Class<?> entityClass) {
        if (!registered.contains(entityClass)) {
            throw new MicroOrmException("Entity not registered: " + entityClass.getName());
        }
        return cache.computeIfAbsent(entityClass, this::build);
    }

    /** Registers an entity class and builds or returns cached metadata. */
    public EntityModel register(Class<?> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass");
        registered.add(entityClass);
        return cache.computeIfAbsent(entityClass, this::build);
    }

    private EntityModel build(Class<?> entityClass) {
        Entity entityAnn = entityClass.getAnnotation(Entity.class);
        if (entityAnn == null) {
            throw new MicroOrmException("Missing @Entity on " + entityClass.getName());
        }
        String table = entityAnn.name().isBlank() ? defaultTableName(entityClass) : entityAnn.name();
        SqlGenerator.validateIdentifier(table, "table");
        try {
            ReflectionUtil.getConstructor(entityClass);
        } catch (NoSuchMethodException e) {
            throw new MicroOrmException("Entity requires a no-arg constructor: " + entityClass.getName(), e);
        }

        rejectInheritedMappedFields(entityClass);

        List<EntityField> fields = new ArrayList<>();
        EntityField pk = null;
        for (Field f : entityClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.getAnnotation(Transient.class) != null) {
                continue;
            }
            f.trySetAccessible();
            Id idAnn = f.getAnnotation(Id.class);
            Column colAnn = f.getAnnotation(Column.class);
            if (idAnn != null) {
                if (pk != null) {
                    throw new MicroOrmException("Multiple @Id fields on " + entityClass.getName());
                }
                validateIdField(entityClass, f, idAnn);
                String col = columnName(f, colAnn);
                boolean nullable = colAnn != null && colAnn.nullable();
                pk = new EntityField(
                        f,
                        col,
                        true,
                        idAnn.autoIncrement(),
                        nullable,
                        colAnn != null && colAnn.unique(),
                        colAnn != null && colAnn.indexed(),
                        colAnn == null ? "" : colAnn.sqlType(),
                        colAnn == null ? 0 : colAnn.length());
                fields.add(pk);
            } else {
                validateFieldType(entityClass, f);
                String col = columnName(f, colAnn);
                boolean nullable = colAnn == null || colAnn.nullable();
                fields.add(new EntityField(
                        f,
                        col,
                        false,
                        false,
                        nullable,
                        colAnn != null && colAnn.unique(),
                        colAnn != null && colAnn.indexed(),
                        colAnn == null ? "" : colAnn.sqlType(),
                        colAnn == null ? 0 : colAnn.length()));
            }
        }
        if (pk == null) {
            throw new MicroOrmException("Missing @Id on " + entityClass.getName());
        }
        EntityModel model = new EntityModel(entityClass, table, fields, pk);
        SqlGenerator.validateColumnNames(model);
        return model;
    }

    private static void validateIdField(Class<?> entityClass, Field field, Id idAnn) {
        validateFieldType(entityClass, field);
        Class<?> type = ReflectionUtil.primitiveToWrapper(field.getType());
        boolean numeric = Number.class.isAssignableFrom(type);
        boolean uuid = type == UUID.class;
        if (!numeric && !uuid) {
            throw new MicroOrmException("@Id field must be numeric or UUID on "
                    + entityClass.getName() + "." + field.getName());
        }
        if (idAnn.autoIncrement() && !numeric) {
            throw new MicroOrmException("@Id(autoIncrement = true) requires a numeric field on "
                    + entityClass.getName() + "." + field.getName());
        }
    }

    private static String columnName(Field f, Column colAnn) {
        if (colAnn != null && !colAnn.name().isBlank()) {
            return colAnn.name();
        }
        return f.getName();
    }

    private static void validateFieldType(Class<?> entityClass, Field field) {
        if (!SUPPORTED_FIELD_TYPES.contains(field.getType())) {
            throw new MicroOrmException("Unsupported field type " + field.getType().getName()
                    + " on " + entityClass.getName() + "." + field.getName());
        }
        Column colAnn = field.getAnnotation(Column.class);
        if (colAnn != null && !colAnn.sqlType().isBlank()) {
            SqlGenerator.validateSqlType(colAnn.sqlType(), entityClass.getName() + "." + field.getName());
        }
    }

    private static void rejectInheritedMappedFields(Class<?> entityClass) {
        Class<?> superClass = entityClass.getSuperclass();
        if (superClass == null || superClass == Object.class) {
            return;
        }
        for (Field f : superClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.getAnnotation(Transient.class) != null) {
                continue;
            }
            if (f.getAnnotation(Id.class) != null || f.getAnnotation(Column.class) != null) {
                throw new MicroOrmException("Entity inheritance is not supported; move mapped fields to "
                        + entityClass.getName() + " (found " + superClass.getName() + "." + f.getName() + ")");
            }
        }
    }

    private static String defaultTableName(Class<?> c) {
        return c.getSimpleName().toLowerCase();
    }
}
