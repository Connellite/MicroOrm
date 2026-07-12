package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.UuidStorage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        return "DROP TABLE IF EXISTS " + model.sqlTableName(dialect);
    }

    @Override
    protected String createIndexDdl(EntityModel model, EntityField field) {
        String indexName = indexName(model, field);
        String indexSql = dialect.sqlName(SqlIdentifier.unquoted(indexName));
        if (model.hasSchema()) {
            indexSql = dialect.sqlName(model.schemaIdentifier()) + "." + indexSql;
        }
        return "CREATE INDEX IF NOT EXISTS " + indexSql
                + " ON " + dialect.sqlName(model.tableIdentifier()) + " (" + dialect.sqlName(field.columnIdentifier()) + ")";
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
        if (t == String.class) {
            return "TEXT";
        }
        if (t == java.util.UUID.class) {
            UuidStorage storage = dialect.valueMapper().uuidStorage();
            return storage == UuidStorage.BINARY || storage == UuidStorage.MICROSOFT_GUID ? "BLOB" : "TEXT";
        }
        throw new IllegalArgumentException("Unsupported field type for SQLite DDL: " + t.getName());
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(EntityField field) {
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    @Override
    protected Set<String> existingColumns(Connection connection, EntityModel model) throws SQLException {
        Set<String> columns = caseInsensitiveNullSkippingSet();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(pragmaTableInfo(model))) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private String pragmaTableInfo(EntityModel model) {
        if (!model.hasSchema()) {
            return "PRAGMA table_info(" + dialect.sqlName(model.tableIdentifier()) + ")";
        }
        return "PRAGMA " + dialect.sqlName(model.schemaIdentifier())
                + ".table_info(" + dialect.sqlName(model.tableIdentifier()) + ")";
    }
}
