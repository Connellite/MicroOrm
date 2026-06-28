package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.util.List;

/**
 * Immutable metadata for one {@link io.github.connellite.microorm.annotation.Entity} class:
 * schema/table name, columns, primary key, and association descriptors.
 *
 * @param entityClass         mapped Java type
 * @param tableIdentifier     physical table name (with quoting hint)
 * @param schemaIdentifier    optional physical schema/catalog name (with quoting hint)
 * @param fields              scalar columns (includes the primary key)
 * @param primaryKey          the {@link io.github.connellite.microorm.annotation.Id} field
 * @param manyToOneRelations  {@link ManyToOneField} descriptors
 * @param oneToManyRelations  {@link OneToManyField} descriptors
 */
public record EntityModel(
        Class<?> entityClass,
        SqlIdentifier tableIdentifier,
        SqlIdentifier schemaIdentifier,
        List<EntityField> fields,
        EntityField primaryKey,
        List<ManyToOneField> manyToOneRelations,
        List<OneToManyField> oneToManyRelations) {

    public EntityModel(Class<?> entityClass, String tableName, List<EntityField> fields, EntityField primaryKey) {
        this(entityClass, SqlIdentifier.unquoted(tableName), null, fields, primaryKey, List.of(), List.of());
    }

    public EntityModel(
            Class<?> entityClass,
            String tableName,
            List<EntityField> fields,
            EntityField primaryKey,
            List<ManyToOneField> manyToOneRelations,
            List<OneToManyField> oneToManyRelations) {
        this(entityClass, SqlIdentifier.unquoted(tableName), null, fields, primaryKey, manyToOneRelations, oneToManyRelations);
    }

    public EntityModel(
            Class<?> entityClass,
            SqlIdentifier tableIdentifier,
            List<EntityField> fields,
            EntityField primaryKey,
            List<ManyToOneField> manyToOneRelations,
            List<OneToManyField> oneToManyRelations) {
        this(entityClass, tableIdentifier, null, fields, primaryKey, manyToOneRelations, oneToManyRelations);
    }

    public EntityModel(
            Class<?> entityClass,
            SqlIdentifier tableIdentifier,
            SqlIdentifier schemaIdentifier,
            List<EntityField> fields,
            EntityField primaryKey,
            List<ManyToOneField> manyToOneRelations,
            List<OneToManyField> oneToManyRelations) {
        this.entityClass = entityClass;
        this.tableIdentifier = tableIdentifier;
        this.schemaIdentifier = schemaIdentifier;
        this.fields = List.copyOf(fields);
        this.primaryKey = primaryKey;
        this.manyToOneRelations = List.copyOf(manyToOneRelations);
        this.oneToManyRelations = List.copyOf(oneToManyRelations);
    }

    /** Logical table name text (without SQL quoting). */
    public String tableName() {
        return tableIdentifier.text();
    }

    /** Logical schema/catalog name text (without SQL quoting), or {@code null} when unspecified. */
    public String schemaName() {
        return schemaIdentifier == null ? null : schemaIdentifier.text();
    }

    /** {@code true} when the entity table should be qualified by a schema/catalog name. */
    public boolean hasSchema() {
        return schemaIdentifier != null;
    }

    /** Renders the table name for SQL, including {@code schema.} when declared. */
    public String sqlTableName(Dialect dialect) {
        String table = dialect.sqlName(tableIdentifier);
        if (schemaIdentifier == null) {
            return table;
        }
        return dialect.sqlName(schemaIdentifier) + "." + table;
    }

    /** Renders the schema/catalog name as it appears in JDBC metadata, or {@code null} when unspecified. */
    public String catalogSchemaName(Dialect dialect) {
        return schemaIdentifier == null ? null : dialect.catalogName(schemaIdentifier);
    }

    /** Renders the table name as it appears in JDBC metadata. */
    public String catalogTableName(Dialect dialect) {
        return dialect.catalogName(tableIdentifier);
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
