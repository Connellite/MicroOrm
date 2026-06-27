package io.github.connellite.microorm.dynamic.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dynamic.Column;
import io.github.connellite.microorm.dynamic.DynamicTable;
import io.github.connellite.microorm.dynamic.LogicalType;

/** MySQL / MariaDB DDL for runtime tables. */
public final class MysqlDynamicSchemaManager extends AbstractDynamicSchemaManager {

    public MysqlDynamicSchemaManager(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String dropTableDdl(DynamicTable table) {
        return "DROP TABLE IF EXISTS " + dialect.sqlName(table.tableIdentifier());
    }

    @Override
    protected String baseTypeForLogical(LogicalType type, int length) {
        return switch (type) {
            case STRING -> "VARCHAR(" + (length > 0 ? length : 255) + ")";
            case TEXT -> "TEXT";
            case INT -> "INT";
            case LONG -> "BIGINT";
            case BOOL -> "BOOLEAN";
            case UUID -> "BINARY(16)";
            case DECIMAL -> "DECIMAL(19,4)";
            case DOUBLE -> "DOUBLE";
            case DATETIME -> "DATETIME";
            case DATE -> "DATE";
        };
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(Column column) {
        return baseType(column) + " AUTO_INCREMENT PRIMARY KEY";
    }
}
