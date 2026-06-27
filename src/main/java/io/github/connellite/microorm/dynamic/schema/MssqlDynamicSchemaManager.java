package io.github.connellite.microorm.dynamic.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dynamic.Column;
import io.github.connellite.microorm.dynamic.DynamicTable;
import io.github.connellite.microorm.dynamic.LogicalType;

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
            case UUID -> "BINARY(16)";
            case DECIMAL -> "DECIMAL(19,4)";
            case DOUBLE -> "FLOAT";
            case DATETIME -> "DATETIME2";
            case DATE -> "DATE";
        };
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(Column column) {
        return baseType(column) + " IDENTITY(1,1) PRIMARY KEY";
    }
}
