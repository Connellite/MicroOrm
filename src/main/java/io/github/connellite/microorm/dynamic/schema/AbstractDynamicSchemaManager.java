package io.github.connellite.microorm.dynamic.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dynamic.Column;
import io.github.connellite.microorm.dynamic.DynamicTable;
import io.github.connellite.microorm.dynamic.LogicalType;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.util.SqlDebugLog;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Shared DDL logic for runtime tables. Subclasses map {@link LogicalType} to dialect-specific SQL types.
 */
public abstract class AbstractDynamicSchemaManager implements DynamicSchemaManager {

    private static final Pattern INDEX_NAME_SANITIZER = Pattern.compile("[^a-zA-Z0-9_]");

    protected final Dialect dialect;

    protected AbstractDynamicSchemaManager(Dialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public void createTable(Connection connection, DynamicTable table) throws SQLException {
        if (!existingColumns(connection, table).isEmpty()) {
            createIndexes(connection, table);
            return;
        }
        try (Statement st = connection.createStatement()) {
            executeSql(st, buildCreateTableDdl(table));
        }
        createIndexes(connection, table);
    }

    @Override
    public void syncTable(Connection connection, DynamicTable table) throws SQLException {
        Set<String> existingColumns = existingColumns(connection, table);
        if (existingColumns.isEmpty()) {
            createTable(connection, table);
            return;
        }
        try (Statement st = connection.createStatement()) {
            for (Column column : table.columns()) {
                if (existingColumns.contains(normalize(dialect.catalogName(column.columnIdentifier())))) {
                    continue;
                }
                validateAddColumn(table, column);
                executeSql(st, "ALTER TABLE " + dialect.sqlName(table.tableIdentifier()) + " ADD "
                        + columnDefinition(column, false));
            }
        }
        createIndexes(connection, table);
    }

    @Override
    public void dropTable(Connection connection, DynamicTable table) throws SQLException {
        if (existingColumns(connection, table).isEmpty()) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            executeSql(st, dropTableDdl(table));
        }
    }

    @Override
    public boolean tableExists(Connection connection, DynamicTable table) throws SQLException {
        return !existingColumns(connection, table).isEmpty();
    }

    protected String buildCreateTableDdl(DynamicTable table) {
        StringJoiner columns = new StringJoiner(", ");
        for (Column column : table.columns()) {
            columns.add(columnDefinition(column, true));
        }
        return "CREATE TABLE " + dialect.sqlName(table.tableIdentifier()) + " (" + columns + ")";
    }

    protected String columnDefinition(Column column, boolean includeUnique) {
        StringBuilder sb = new StringBuilder();
        sb.append(dialect.sqlName(column.columnIdentifier())).append(' ');
        if (column.primaryKey() && column.autoIncrement()) {
            sb.append(autoIncrementPrimaryKeyDefinition(column));
        } else if (column.primaryKey()) {
            sb.append(baseType(column)).append(" NOT NULL PRIMARY KEY");
        } else {
            sb.append(baseType(column));
            if (!column.nullable()) {
                sb.append(" NOT NULL");
            }
            if (includeUnique && column.unique()) {
                sb.append(" UNIQUE");
            }
        }
        return sb.toString();
    }

    protected String baseType(Column column) {
        if (!column.sqlTypeOverride().isBlank()) {
            return column.sqlTypeOverride();
        }
        return baseTypeForLogical(column.type(), column.length());
    }

    protected abstract String baseTypeForLogical(LogicalType type, int length);

    protected abstract String autoIncrementPrimaryKeyDefinition(Column column);

    protected Set<String> existingColumns(Connection connection, DynamicTable table) throws SQLException {
        Set<String> columns = new HashSet<>();
        String tableName = dialect.catalogName(table.tableIdentifier());
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                columns.add(normalize(rs.getString("COLUMN_NAME")));
            }
        }
        if (columns.isEmpty()) {
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, tableName.toUpperCase(Locale.ROOT), null)) {
                while (rs.next()) {
                    columns.add(normalize(rs.getString("COLUMN_NAME")));
                }
            }
        }
        return columns;
    }

    protected void createIndexes(Connection connection, DynamicTable table) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (Column column : table.columns()) {
                if (!column.indexed() || column.primaryKey()) {
                    continue;
                }
                if (indexExists(connection, table, column)) {
                    continue;
                }
                executeSql(st, createIndexDdl(table, column));
            }
        }
    }

    protected boolean indexExists(Connection connection, DynamicTable table, Column column) throws SQLException {
        String indexName = indexName(table, column);
        if (findIndex(connection, dialect.catalogName(table.tableIdentifier()), indexName)) {
            return true;
        }
        return findIndex(connection, dialect.catalogName(table.tableIdentifier()).toUpperCase(Locale.ROOT), indexName);
    }

    protected String createIndexDdl(DynamicTable table, Column column) {
        String indexName = indexName(table, column);
        return "CREATE INDEX " + dialect.sqlName(SqlIdentifier.unquoted(indexName))
                + " ON " + dialect.sqlName(table.tableIdentifier()) + " (" + dialect.sqlName(column.columnIdentifier()) + ")";
    }

    protected String dropTableDdl(DynamicTable table) {
        return "DROP TABLE " + dialect.sqlName(table.tableIdentifier());
    }

    protected void validateAddColumn(DynamicTable table, Column column) {
        if (column.primaryKey()) {
            throw new MicroOrmException("Cannot add missing primary key column to existing table: "
                    + table.tableName() + "." + column.name());
        }
        if (!column.nullable()) {
            throw new MicroOrmException("Cannot add NOT NULL column without a default to existing table: "
                    + table.tableName() + "." + column.name());
        }
        if (column.unique()) {
            throw new MicroOrmException("Cannot add UNIQUE column to existing table: "
                    + table.tableName() + "." + column.name());
        }
    }

    protected String indexName(DynamicTable table, Column column) {
        return INDEX_NAME_SANITIZER.matcher("idx_" + table.tableName() + "_" + column.name()).replaceAll("_");
    }

    protected String normalize(String identifier) {
        return identifier == null ? "" : identifier.toLowerCase(Locale.ROOT);
    }

    private boolean findIndex(Connection connection, String tableName, String indexName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name != null && normalize(name).equals(normalize(indexName))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void executeSql(Statement statement, String sql) throws SQLException {
        SqlDebugLog.sql("dynamic-schema", sql);
        statement.execute(sql);
    }
}
