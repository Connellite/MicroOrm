package io.github.connellite.stoneorm.mapping;

import java.util.List;

public record EntityModel(Class<?> entityClass, String tableName, List<EntityField> fields, EntityField primaryKey) {

    public EntityModel(Class<?> entityClass, String tableName, List<EntityField> fields, EntityField primaryKey) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.fields = List.copyOf(fields);
        this.primaryKey = primaryKey;
    }
}
