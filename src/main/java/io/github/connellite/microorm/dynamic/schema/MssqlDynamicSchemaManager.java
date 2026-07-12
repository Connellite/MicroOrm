package io.github.connellite.microorm.dynamic.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dynamic.Column;
import io.github.connellite.microorm.dynamic.LogicalType;
import io.github.connellite.microorm.type.UuidStorage;

/** Microsoft SQL Server DDL for runtime tables. */
public final class MssqlDynamicSchemaManager extends AbstractDynamicSchemaManager {

    public MssqlDynamicSchemaManager(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String baseTypeForLogical(LogicalType type, int length) {
        return switch (type) {
            case STRING -> "NVARCHAR(" + (length > 0 ? length : 255) + ")";
            case TEXT -> "NVARCHAR(MAX)";
            case INT -> "INT";
            case LONG -> "BIGINT";
            case BOOL -> "BIT";
            case UUID -> uuidType();
            case DECIMAL -> "DECIMAL(19,4)";
            case DOUBLE -> "FLOAT";
            case DATETIME -> "DATETIME2";
            case DATE -> "DATE";
        };
    }

    private String uuidType() {
        UuidStorage storage = dialect.valueMapper().uuidStorage();
        if (storage == UuidStorage.MICROSOFT_GUID || storage == UuidStorage.NATIVE) {
            return "UNIQUEIDENTIFIER";
        }
        if (storage == UuidStorage.STRING) {
            return "NVARCHAR(36)";
        }
        return "BINARY(16)";
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(Column column) {
        return baseType(column) + " IDENTITY(1,1) PRIMARY KEY";
    }
}
