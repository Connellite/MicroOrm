package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.sql.SqlIdentifier;

import java.util.List;

public record EntityModel(
        Class<?> entityClass,
        SqlIdentifier tableIdentifier,
        List<EntityField> fields,
        EntityField primaryKey,
        List<ManyToOneField> manyToOneRelations,
        List<OneToManyField> oneToManyRelations) {

    public EntityModel(Class<?> entityClass, String tableName, List<EntityField> fields, EntityField primaryKey) {
        this(entityClass, SqlIdentifier.unquoted(tableName), fields, primaryKey, List.of(), List.of());
    }

    public EntityModel(
            Class<?> entityClass,
            String tableName,
            List<EntityField> fields,
            EntityField primaryKey,
            List<ManyToOneField> manyToOneRelations,
            List<OneToManyField> oneToManyRelations) {
        this(entityClass, SqlIdentifier.unquoted(tableName), fields, primaryKey, manyToOneRelations, oneToManyRelations);
    }

    public EntityModel(
            Class<?> entityClass,
            SqlIdentifier tableIdentifier,
            List<EntityField> fields,
            EntityField primaryKey,
            List<ManyToOneField> manyToOneRelations,
            List<OneToManyField> oneToManyRelations) {
        this.entityClass = entityClass;
        this.tableIdentifier = tableIdentifier;
        this.fields = List.copyOf(fields);
        this.primaryKey = primaryKey;
        this.manyToOneRelations = List.copyOf(manyToOneRelations);
        this.oneToManyRelations = List.copyOf(oneToManyRelations);
    }

    public String tableName() {
        return tableIdentifier.text();
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
