package io.github.connellite.stoneorm.mapping;

import java.util.List;

public final class EntityModel {

    private final Class<?> entityClass;
    private final String tableName;
    private final List<EntityField> fields;
    private final EntityField primaryKey;

    public EntityModel(Class<?> entityClass, String tableName, List<EntityField> fields, EntityField primaryKey) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.fields = List.copyOf(fields);
        this.primaryKey = primaryKey;
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public String tableName() {
        return tableName;
    }

    public List<EntityField> fields() {
        return fields;
    }

    public EntityField primaryKey() {
        return primaryKey;
    }
}
