package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public final class SqliteSchemaManager extends AbstractSchemaManager {

    public SqliteSchemaManager(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String buildCreateTableDdl(EntityModel model) {
        return super.buildCreateTableDdl(model).replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ");
    }

    @Override
    protected String dropTableDdl(EntityModel model) {
        return "DROP TABLE IF EXISTS " + dialect.quote(model.tableName());
    }

    @Override
    protected String createIndexDdl(EntityModel model, EntityField field) {
        String indexName = indexName(model, field);
        return "CREATE INDEX IF NOT EXISTS " + dialect.quote(indexName)
                + " ON " + dialect.quote(model.tableName()) + " (" + dialect.quote(field.columnName()) + ")";
    }

    @Override
    protected String baseTypeForJava(Class<?> t, int length) {
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

    @Override
    protected String autoIncrementPrimaryKeyDefinition(EntityField field) {
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    @Override
    protected Set<String> existingColumns(Connection connection, EntityModel model) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + dialect.quote(model.tableName()) + ")")) {
            while (rs.next()) {
                columns.add(normalize(rs.getString("name")));
            }
        }
        return columns;
    }
}
