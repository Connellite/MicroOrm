package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.ManyToManyField;
import io.github.connellite.microorm.type.UuidStorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

public final class MysqlSchemaManager extends AbstractSchemaManager {

    public MysqlSchemaManager(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String baseTypeForJava(Class<?> t, int length) {
        if (t == long.class || t == Long.class) {
            return "BIGINT";
        }
        if (t == int.class || t == Integer.class || t == short.class || t == Short.class || t == byte.class || t == Byte.class) {
            return "INT";
        }
        if (t == boolean.class || t == Boolean.class) {
            return "BOOLEAN";
        }
        if (t == double.class || t == Double.class) {
            return "DOUBLE";
        }
        if (t == float.class || t == Float.class) {
            return "FLOAT";
        }
        if (t == String.class) {
            return "VARCHAR(" + (length > 0 ? length : 255) + ")";
        }
        if (t == UUID.class) {
            UuidStorage storage = dialect.valueMapper().uuidStorage();
            if (storage == UuidStorage.MICROSOFT_GUID) {
                throw new IllegalArgumentException("MICROSOFT_GUID UUID storage is supported only by MSSQL DDL");
            }
            return storage == UuidStorage.STRING ? "CHAR(36)" : "BINARY(16)";
        }
        throw new IllegalArgumentException("Unsupported field type for MySQL DDL: " + t.getName());
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(EntityField field) {
        return baseType(field) + " AUTO_INCREMENT PRIMARY KEY";
    }

    @Override
    protected String dropTableDdl(EntityModel model) {
        return "DROP TABLE IF EXISTS " + model.sqlTableName(dialect);
    }

    @Override
    protected String dropJoinTableDdl(ManyToManyField relation) {
        return "DROP TABLE IF EXISTS " + relation.sqlJoinTableName(dialect);
    }

    @Override
    protected String buildCreateJoinTableDdl(ManyToManyField relation) {
        return super.buildCreateJoinTableDdl(relation).replaceFirst("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ");
    }

    @Override
    protected boolean dropTableIfMissingIsSafe() {
        return true;
    }

    @Override
    protected boolean dropJoinTableIfMissingIsSafe() {
        return true;
    }

    @Override
    protected boolean createJoinTableIfExistsIsSafe() {
        return true;
    }

    @Override
    protected String buildCreateTableDdl(EntityModel model) {
        String ddl = super.buildCreateTableDdl(model);
        return model.comment().isBlank() ? ddl : ddl + " COMMENT=" + sqlStringLiteral(model.comment());
    }

    @Override
    protected String inlineColumnComment(EntityField field) {
        return field.comment().isBlank() ? "" : "COMMENT " + sqlStringLiteral(field.comment());
    }

    @Override
    protected String metadataCatalog(EntityModel model) {
        return model.catalogSchemaName(dialect);
    }

    @Override
    protected String metadataSchema(EntityModel model) {
        return null;
    }

    @Override
    protected Set<String> existingColumns(Connection connection, EntityModel model) throws SQLException {
        Set<String> columns = caseInsensitiveNullSkippingSet();
        String catalog = currentCatalog(connection, model);
        String table = model.catalogTableName(dialect);
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                """)) {
            ps.setString(1, catalog);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }

    @Override
    protected boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        String catalog = schema != null ? schema : connection.getCatalog();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ? AND table_type = 'BASE TABLE'
                """)) {
            ps.setString(1, catalog);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    protected boolean indexExists(Connection connection, EntityModel model, String indexName) throws SQLException {
        String catalog = currentCatalog(connection, model);
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT 1
                FROM information_schema.statistics
                WHERE table_schema = ? AND table_name = ? AND index_name = ?
                LIMIT 1
                """)) {
            ps.setString(1, catalog);
            ps.setString(2, model.catalogTableName(dialect));
            ps.setString(3, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String currentCatalog(Connection connection, EntityModel model) throws SQLException {
        String catalog = model.catalogSchemaName(dialect);
        if (catalog == null || catalog.isBlank()) {
            return connection.getCatalog();
        }
        return catalog;
    }
}
