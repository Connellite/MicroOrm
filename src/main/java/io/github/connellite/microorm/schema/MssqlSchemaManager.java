package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.mapping.EntityField;

import java.util.UUID;

public final class MssqlSchemaManager extends AbstractSchemaManager {

    public MssqlSchemaManager(Dialect dialect) {
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
            return "BIT";
        }
        if (t == double.class || t == Double.class) {
            return "FLOAT";
        }
        if (t == float.class || t == Float.class) {
            return "REAL";
        }
        if (t == String.class) {
            return "NVARCHAR(" + (length > 0 ? length : 255) + ")";
        }
        if (t == UUID.class) {
            return "BINARY(16)";
        }
        throw new IllegalArgumentException("Unsupported field type for MSSQL DDL: " + t.getName());
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(EntityField field) {
        return baseType(field) + " IDENTITY(1,1) PRIMARY KEY";
    }
}
