package io.github.connellite.stoneorm.dialect;

import io.github.connellite.stoneorm.mapping.EntityField;
import io.github.connellite.stoneorm.mapping.EntityModel;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite 3 — identifiers quoted as "name". */
public final class SqliteDialect implements Dialect {

    public static final SqliteDialect INSTANCE = new SqliteDialect();

    private SqliteDialect() {
    }

    @Override
    public String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    @Override
    public void createTable(Connection c, EntityModel model) throws SQLException {
        String ddl = buildCreateTableDdl(model);
        try (Statement st = c.createStatement()) {
            st.execute(ddl);
        }
    }

    @Override
    public void dropTable(Connection c, EntityModel model) throws SQLException {
        String sql = "DROP TABLE IF EXISTS " + quote(model.tableName());
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    String buildCreateTableDdl(EntityModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quote(model.tableName())).append(" (");
        boolean first = true;
        for (EntityField f : model.fields()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(quote(f.columnName())).append(' ');
            if (f.id() && f.autoIncrement()) {
                sb.append("INTEGER PRIMARY KEY AUTOINCREMENT");
            } else if (f.id()) {
                sb.append(baseSqliteType(f)).append(" NOT NULL PRIMARY KEY");
            } else {
                sb.append(baseSqliteType(f));
                if (!f.nullable()) {
                    sb.append(" NOT NULL");
                }
            }
        }
        sb.append(')');
        return sb.toString();
    }

    private String baseSqliteType(EntityField f) {
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
}
