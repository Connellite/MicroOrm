package io.github.connellite.microorm.dynamic.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dynamic.Column;
import io.github.connellite.microorm.dynamic.DynamicTable;
import io.github.connellite.microorm.dynamic.LogicalType;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

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
    protected Set<String> existingColumns(Connection connection, DynamicTable table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + dialect.sqlName(table.tableIdentifier()) + ")")) {
            while (rs.next()) {
                columns.add(normalize(rs.getString("name")));
            }
        }
        return columns;
    }

    @Override
    protected String baseTypeForLogical(LogicalType type, int length) {
        return switch (type) {
            case STRING, TEXT, UUID -> "TEXT";
            case INT, LONG, BOOL -> "INTEGER";
            case DECIMAL, DOUBLE -> "REAL";
            case DATETIME, DATE -> "TEXT";
        };
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(Column column) {
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }
}
