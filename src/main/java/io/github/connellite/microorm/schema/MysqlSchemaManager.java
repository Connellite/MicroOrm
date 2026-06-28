package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;

import java.util.UUID;

public final class MysqlSchemaManager extends AbstractSchemaManager {

    public MysqlSchemaManager(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String baseTypeForJava(Class<?> t, int length) {
        if (t == long.class || t == Long.class) {
            return "BIGINT";
        }
        if (t == int.class || t == Integer.class || t == short.class || t == Short.class || t == byte.class || t == Byte.class) {
            return "INT";
        }
        if (t == boolean.class || t == Boolean.class) {
            return "BOOLEAN";
        }
        if (t == double.class || t == Double.class) {
            return "DOUBLE";
        }
        if (t == float.class || t == Float.class) {
            return "FLOAT";
        }
        if (t == String.class) {
            return "VARCHAR(" + (length > 0 ? length : 255) + ")";
        }
        if (t == UUID.class) {
            return "BINARY(16)";
        }
        throw new IllegalArgumentException("Unsupported field type for MySQL DDL: " + t.getName());
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(EntityField field) {
        return baseType(field) + " AUTO_INCREMENT PRIMARY KEY";
    }

    @Override
    protected String dropTableDdl(EntityModel model) {
        return "DROP TABLE IF EXISTS " + model.sqlTableName(dialect);
    }

    @Override
    protected String metadataCatalog(EntityModel model) {
        return model.catalogSchemaName(dialect);
    }

    @Override
    protected String metadataSchema(EntityModel model) {
        return null;
    }
}
