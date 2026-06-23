package io.github.connellite.stoneorm.schema;

import io.github.connellite.stoneorm.StoneOrmException;
import io.github.connellite.stoneorm.dialect.Dialect;
import io.github.connellite.stoneorm.mapping.EntityField;
import io.github.connellite.stoneorm.mapping.EntityModel;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public final class SqliteSchemaManager implements SchemaManager {

    private final Dialect dialect;

    public SqliteSchemaManager(Dialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public void createTable(Connection connection, EntityModel model) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(buildCreateTableDdl(model));
        }
        createIndexes(connection, model);
    }

    @Override
    public void syncTable(Connection connection, EntityModel model) throws SQLException {
        Set<String> existingColumns = existingColumns(connection, model);
        if (existingColumns.isEmpty()) {
            createTable(connection, model);
            return;
        }
        try (Statement st = connection.createStatement()) {
            for (EntityField f : model.fields()) {
                if (existingColumns.contains(f.columnName())) {
                    continue;
                }
                if (f.id()) {
                    throw new StoneOrmException("Cannot add missing primary key column to existing SQLite table: "
                            + model.tableName() + "." + f.columnName());
                }
                if (!f.nullable()) {
                    throw new StoneOrmException("Cannot add NOT NULL column without a default to existing SQLite table: "
                            + model.tableName() + "." + f.columnName());
                }
                if (f.unique()) {
                    throw new StoneOrmException("Cannot add UNIQUE column to existing SQLite table: "
                            + model.tableName() + "." + f.columnName());
                }
                st.execute("ALTER TABLE " + dialect.quote(model.tableName()) + " ADD COLUMN " + columnDefinition(f, false));
            }
        }
        createIndexes(connection, model);
    }

    @Override
    public void dropTable(Connection connection, EntityModel model) throws SQLException {
        String sql = "DROP TABLE IF EXISTS " + dialect.quote(model.tableName());
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    String buildCreateTableDdl(EntityModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(dialect.quote(model.tableName())).append(" (");
        boolean first = true;
        for (EntityField f : model.fields()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(columnDefinition(f, true));
        }
        sb.append(')');
        return sb.toString();
    }

    private String columnDefinition(EntityField f, boolean includeUnique) {
        StringBuilder sb = new StringBuilder();
        sb.append(dialect.quote(f.columnName())).append(' ');
        if (f.id() && f.autoIncrement()) {
            sb.append("INTEGER PRIMARY KEY AUTOINCREMENT");
        } else if (f.id()) {
            sb.append(baseSqliteType(f)).append(" NOT NULL PRIMARY KEY");
        } else {
            sb.append(baseSqliteType(f));
            if (!f.nullable()) {
                sb.append(" NOT NULL");
            }
            if (includeUnique && f.unique()) {
                sb.append(" UNIQUE");
            }
        }
        return sb.toString();
    }

    private String baseSqliteType(EntityField f) {
        if (!f.sqlType().isBlank()) {
            return f.sqlType();
        }
        Class<?> t = f.javaType();
        if (t == long.class || t == Long.class || t == int.class || t == Integer.class
                || t == short.class || t == Short.class || t == byte.class || t == Byte.class) {
            return "INTEGER";
        }
        if (t == boolean.class || t == Boolean.class) {
            return "INTEGER";
        }
        if (t == double.class || t == Double.class || t == float.class || t == Float.class) {
            return "REAL";
        }
        if (t == String.class || t == java.util.UUID.class) {
            return "TEXT";
        }
        throw new IllegalArgumentException("Unsupported field type for SQLite DDL: " + t.getName());
    }

    private Set<String> existingColumns(Connection connection, EntityModel model) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + dialect.quote(model.tableName()) + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private void createIndexes(Connection connection, EntityModel model) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (EntityField f : model.fields()) {
                if (!f.indexed() || f.id()) {
                    continue;
                }
                String indexName = "idx_" + model.tableName() + "_" + f.columnName();
                indexName = indexName.replaceAll("[^a-zA-Z0-9_]", "_");
                st.execute("CREATE INDEX IF NOT EXISTS " + dialect.quote(indexName)
                        + " ON " + dialect.quote(model.tableName()) + " (" + dialect.quote(f.columnName()) + ")");
            }
        }
    }
}
