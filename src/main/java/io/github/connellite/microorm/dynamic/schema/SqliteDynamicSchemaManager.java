package io.github.connellite.microorm.dynamic.schema;

import io.github.connellite.collections.DelegatingNullSkippingCollection;
import io.github.connellite.collections.LinkedCaseInsensitiveSet;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dynamic.Column;
import io.github.connellite.microorm.dynamic.DynamicTable;
import io.github.connellite.microorm.dynamic.LogicalType;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.UuidStorage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

/** SQLite DDL for runtime tables. */
public final class SqliteDynamicSchemaManager extends AbstractDynamicSchemaManager {

    public SqliteDynamicSchemaManager(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String buildCreateTableDdl(DynamicTable table) {
        return super.buildCreateTableDdl(table).replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ");
    }

    @Override
    protected String dropTableDdl(DynamicTable table) {
        return "DROP TABLE IF EXISTS " + dialect.sqlName(table.tableIdentifier());
    }

    @Override
    protected String createIndexDdl(DynamicTable table, Column column) {
        String indexName = indexName(table, column);
        return "CREATE INDEX IF NOT EXISTS " + dialect.sqlName(SqlIdentifier.unquoted(indexName))
                + " ON " + dialect.sqlName(table.tableIdentifier()) + " (" + dialect.sqlName(column.columnIdentifier()) + ")";
    }

    @Override
    protected Collection<String> existingColumns(Connection connection, DynamicTable table) throws SQLException {
        Collection<String> columns = new DelegatingNullSkippingCollection<>(new LinkedCaseInsensitiveSet());
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + dialect.sqlName(table.tableIdentifier()) + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    @Override
    protected String baseTypeForLogical(LogicalType type, int length) {
        return switch (type) {
            case STRING, TEXT -> "TEXT";
            case UUID -> uuidType();
            case INT, LONG, BOOL -> "INTEGER";
            case DECIMAL, DOUBLE -> "REAL";
            case DATETIME, DATE -> "TEXT";
        };
    }

    private String uuidType() {
        UuidStorage storage = dialect.valueMapper().uuidStorage();
        if (storage == UuidStorage.MICROSOFT_GUID) {
            throw new IllegalArgumentException("MICROSOFT_GUID UUID storage is supported only by MSSQL DDL");
        }
        return storage == UuidStorage.BINARY ? "BLOB" : "TEXT";
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(Column column) {
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }
}
