package io.github.connellite.microorm.mapping;

import java.util.List;

public record EntityModel(
        Class<?> entityClass,
        String tableName,
        List<EntityField> fields,
        EntityField primaryKey,
        List<ManyToOneField> manyToOneRelations,
        List<OneToManyField> oneToManyRelations) {

    public EntityModel(Class<?> entityClass, String tableName, List<EntityField> fields, EntityField primaryKey) {
        this(entityClass, tableName, fields, primaryKey, List.of(), List.of());
    }

    public EntityModel(
            Class<?> entityClass,
            String tableName,
            List<EntityField> fields,
            EntityField primaryKey,
            List<ManyToOneField> manyToOneRelations,
            List<OneToManyField> oneToManyRelations) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.fields = List.copyOf(fields);
        this.primaryKey = primaryKey;
        this.manyToOneRelations = List.copyOf(manyToOneRelations);
        this.oneToManyRelations = List.copyOf(oneToManyRelations);
    }

    public boolean hasRelations() {
        return !manyToOneRelations.isEmpty() || !oneToManyRelations.isEmpty();
    }

    public ManyToOneField manyToOneByFieldName(String fieldName) {
        for (ManyToOneField relation : manyToOneRelations) {
            if (relation.javaField().getName().equals(fieldName)) {
                return relation;
            }
        }
        throw new io.github.connellite.microorm.MicroOrmException(
                "No @ManyToOne field '" + fieldName + "' on " + entityClass.getName());
    }
}
