package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.util.List;

/**
 * Immutable metadata for one {@link io.github.connellite.microorm.annotation.Entity} class:
 * table name, columns, primary key, and association descriptors.
 *
 * @param entityClass         mapped Java type
 * @param tableIdentifier     physical table name (with quoting hint)
 * @param fields              scalar columns (includes the primary key)
 * @param primaryKey          the {@link io.github.connellite.microorm.annotation.Id} field
 * @param manyToOneRelations  {@link ManyToOneField} descriptors
 * @param oneToManyRelations  {@link OneToManyField} descriptors
 */
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

    /** Logical table name text (without SQL quoting). */
    public String tableName() {
        return tableIdentifier.text();
    }

    /** {@code true} when the entity declares {@code @ManyToOne} or {@code @OneToMany} fields. */
    public boolean hasRelations() {
        return !manyToOneRelations.isEmpty() || !oneToManyRelations.isEmpty();
    }

    /** Returns {@link ManyToOneField} metadata for a Java field name. */
    public ManyToOneField manyToOneByFieldName(String fieldName) {
        for (ManyToOneField relation : manyToOneRelations) {
            if (relation.javaField().getName().equals(fieldName)) {
                return relation;
            }
        }
        throw new MicroOrmException(
                "No @ManyToOne field '" + fieldName + "' on " + entityClass.getName());
    }
}
