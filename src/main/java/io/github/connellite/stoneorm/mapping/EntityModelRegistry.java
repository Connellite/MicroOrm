package io.github.connellite.stoneorm.mapping;

import io.github.connellite.stoneorm.StoneOrmException;
import io.github.connellite.stoneorm.annotation.Column;
import io.github.connellite.stoneorm.annotation.Entity;
import io.github.connellite.stoneorm.annotation.Id;
import io.github.connellite.stoneorm.annotation.Transient;
import io.github.connellite.stoneorm.sql.SqlGenerator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityModelRegistry {

    private final ConcurrentHashMap<Class<?>, EntityModel> cache = new ConcurrentHashMap<>();

    public EntityModel get(Class<?> entityClass) {
        EntityModel m = cache.get(entityClass);
        if (m == null) {
            throw new StoneOrmException("Entity not registered: " + entityClass.getName());
        }
        return m;
    }

    public EntityModel register(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, this::build);
    }

    private EntityModel build(Class<?> entityClass) {
        Entity entityAnn = entityClass.getAnnotation(Entity.class);
        if (entityAnn == null) {
            throw new StoneOrmException("Missing @Entity on " + entityClass.getName());
        }
        String table = entityAnn.name().isBlank() ? defaultTableName(entityClass) : entityAnn.name();
        Constructor<?> ctor;
        try {
            ctor = entityClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new StoneOrmException("Entity requires a no-arg constructor: " + entityClass.getName(), e);
        }
        ctor.trySetAccessible();

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
                    throw new StoneOrmException("Multiple @Id fields on " + entityClass.getName());
                }
                String col = columnName(f, colAnn);
                boolean nullable = colAnn != null && colAnn.nullable();
                pk = new EntityField(f, col, true, idAnn.autoIncrement(), nullable);
                fields.add(pk);
            } else {
                String col = columnName(f, colAnn);
                boolean nullable = colAnn == null || colAnn.nullable();
                fields.add(new EntityField(f, col, false, false, nullable));
            }
        }
        if (pk == null) {
            throw new StoneOrmException("Missing @Id on " + entityClass.getName());
        }
        EntityModel model = new EntityModel(entityClass, table, fields, pk);
        SqlGenerator.validateColumnNames(model);
        return model;
    }

    private static String columnName(Field f, Column colAnn) {
        if (colAnn != null && !colAnn.name().isBlank()) {
            return colAnn.name();
        }
        return f.getName();
    }

    private static String defaultTableName(Class<?> c) {
        return c.getSimpleName().toLowerCase();
    }
}
