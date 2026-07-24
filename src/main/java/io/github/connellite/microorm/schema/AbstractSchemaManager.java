package io.github.connellite.microorm.schema;

import io.github.connellite.collections.CaseInsensitiveHashMap;
import io.github.connellite.collections.DelegatingNullSkippingMap;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.util.Logger;
import io.github.connellite.microorm.util.LoggerFactory;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.TableCheck;
import io.github.connellite.microorm.mapping.TableIndex;
import io.github.connellite.microorm.mapping.TableUniqueConstraint;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public abstract class AbstractSchemaManager implements SchemaManager {

    private static final Pattern INDEX_NAME_SANITIZER = Pattern.compile("[^a-zA-Z0-9_]");

    protected final Dialect dialect;

    protected AbstractSchemaManager(Dialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public void createTable(Connection connection, EntityModel model) throws SQLException {
        requirePhysicalMutableTable(model, "createTable");
        if (!existingColumns(connection, model).isEmpty()) {
            createSchemaObjects(connection, model);
            return;
        }
        try (Statement st = connection.createStatement()) {
            executeSql(st, buildCreateTableDdl(model));
        }
        createSchemaObjects(connection, model);
    }

    @Override
    public void syncTable(Connection connection, EntityModel model) throws SQLException {
        requirePhysicalMutableTable(model, "syncTable");
        Set<String> existingColumns = existingColumns(connection, model);
        if (existingColumns.isEmpty()) {
            createTable(connection, model);
            return;
        }
        try (Statement st = connection.createStatement()) {
            for (EntityField f : model.fields()) {
                if (existingColumns.contains(dialect.catalogName(f.columnIdentifier()))) {
                    continue;
                }
                validateAddColumn(model, f);
                executeSql(st, "ALTER TABLE " + model.sqlTableName(dialect) + " ADD " + columnDefinition(f, false));
            }
            for (ManyToOneField relation : model.manyToOneRelations()) {
                if (existingColumns.contains(dialect.catalogName(relation.joinColumnIdentifier()))) {
                    continue;
                }
                validateAddJoinColumn(model, relation);
                executeSql(st, "ALTER TABLE " + model.sqlTableName(dialect) + " ADD " + joinColumnDefinition(relation));
            }
        }
        createSchemaObjects(connection, model);
    }

    @Override
    public void dropTable(Connection connection, EntityModel model) throws SQLException {
        requirePhysicalMutableTable(model, "dropTable");
        if (existingColumns(connection, model).isEmpty()) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            executeSql(st, dropTableDdl(model));
        }
    }

    protected String buildCreateTableDdl(EntityModel model) {
        StringJoiner columns = new StringJoiner(", ");
        for (EntityField f : model.fields()) {
            columns.add(columnDefinition(f, true));
        }
        for (ManyToOneField relation : model.manyToOneRelations()) {
            columns.add(joinColumnDefinition(relation));
        }
        for (TableUniqueConstraint constraint : model.uniqueConstraints()) {
            columns.add(uniqueConstraintDefinition(constraint));
        }
        for (TableCheck check : model.checks()) {
            columns.add(checkConstraintDefinition(check));
        }
        for (EntityField field : model.fields()) {
            for (TableCheck check : field.checks()) {
                columns.add(checkConstraintDefinition(check));
            }
        }
        return "CREATE TABLE " + model.sqlTableName(dialect) + " (" + columns + ")";
    }

    protected String joinColumnDefinition(ManyToOneField relation) {
        StringBuilder sb = new StringBuilder();
        sb.append(dialect.sqlName(relation.joinColumnIdentifier())).append(' ');
        sb.append(baseTypeForJava(relation.foreignKeyJavaType(), 0));
        if (!relation.nullable()) {
            sb.append(" NOT NULL");
        }
        return sb.toString();
    }

    protected abstract String baseTypeForJava(Class<?> javaType, int length);

    protected String columnDefinition(EntityField f, boolean includeUnique) {
        StringBuilder sb = new StringBuilder();
        sb.append(dialect.sqlName(f.columnIdentifier())).append(' ');
        if (f.id() && f.autoIncrement()) {
            sb.append(autoIncrementPrimaryKeyDefinition(f));
        } else if (f.id()) {
            sb.append(baseType(f)).append(" NOT NULL PRIMARY KEY");
        } else {
            sb.append(baseType(f));
            if (!f.nullable()) {
                sb.append(" NOT NULL");
            }
            if (includeUnique && f.unique()) {
                sb.append(" UNIQUE");
            }
        }
        if (!f.columnDefault().isBlank()) {
            sb.append(" DEFAULT ").append(f.columnDefault());
        }
        String inlineComment = inlineColumnComment(f);
        if (!inlineComment.isBlank()) {
            sb.append(' ').append(inlineComment);
        }
        return sb.toString();
    }

    protected String baseType(EntityField field) {
        if (!field.sqlType().isBlank()) {
            return field.sqlType();
        }
        return baseTypeForJava(field.jdbcJavaType(), field.length());
    }

    protected Set<String> existingColumns(Connection connection, EntityModel model) throws SQLException {
        Set<String> columns = caseInsensitiveNullSkippingSet();
        String catalog = metadataCatalog(model);
        String schema = metadataSchema(model);
        String table = model.catalogTableName(dialect);
        try (ResultSet rs = connection.getMetaData().getColumns(catalog, schema, table, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        if (columns.isEmpty()) {
            LogHolder.logger.trace(() -> "No columns found for table " + table + ", retrying with uppercase name");
            try (ResultSet rs = connection.getMetaData().getColumns(catalog, schema, table.toUpperCase(Locale.ROOT), null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }

    protected void createIndexes(Connection connection, EntityModel model) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (EntityField f : model.fields()) {
                if (!f.indexed() || f.id()) {
                    continue;
                }
                if (indexExists(connection, model, f)) {
                    continue;
                }
                executeSql(st, createIndexDdl(model, f));
            }
            for (TableIndex index : model.indexes()) {
                if (indexExists(connection, model, index.name())) {
                    continue;
                }
                executeSql(st, createIndexDdl(model, index));
            }
            for (TableUniqueConstraint constraint : model.uniqueConstraints()) {
                if (indexExists(connection, model, constraint.name())) {
                    continue;
                }
                executeSql(st, createUniqueIndexDdl(model, constraint));
            }
        }
    }

    protected void createSchemaObjects(Connection connection, EntityModel model) throws SQLException {
        createIndexes(connection, model);
        applyComments(connection, model);
    }

    private static void executeSql(Statement statement, String sql) throws SQLException {
        LogHolder.logger.debug(() -> "schema: " + sql);
        statement.execute(sql);
    }

    protected boolean indexExists(Connection connection, EntityModel model, EntityField field) throws SQLException {
        String indexName = indexName(model, field);
        return indexExists(connection, model, indexName);
    }

    protected boolean indexExists(Connection connection, EntityModel model, String indexName) throws SQLException {
        String catalog = metadataCatalog(model);
        String schema = metadataSchema(model);
        String table = model.catalogTableName(dialect);
        if (findIndex(connection, catalog, schema, table, indexName)) {
            return true;
        }
        LogHolder.logger.trace(() -> "Index not found for table " + table + ", retrying with uppercase name");
        return findIndex(connection, catalog, schema, table.toUpperCase(Locale.ROOT), indexName);
    }

    private boolean findIndex(
            Connection connection,
            String catalogName,
            String schemaName,
            String tableName,
            String indexName) throws SQLException {
        Set<String> indexes = caseInsensitiveNullSkippingSet();
        try (ResultSet rs = connection.getMetaData().getIndexInfo(catalogName, schemaName, tableName, false, false)) {
            while (rs.next()) {
                indexes.add(rs.getString("INDEX_NAME"));
            }
        }
        return indexes.contains(indexName);
    }

    protected String metadataCatalog(EntityModel model) {
        return null;
    }

    protected String metadataSchema(EntityModel model) {
        return model.catalogSchemaName(dialect);
    }

    protected String createIndexDdl(EntityModel model, EntityField field) {
        String indexName = indexName(model, field);
        return "CREATE INDEX " + dialect.sqlName(SqlIdentifier.unquoted(indexName))
                + " ON " + model.sqlTableName(dialect) + " (" + dialect.sqlName(field.columnIdentifier()) + ")";
    }

    protected String createIndexDdl(EntityModel model, TableIndex index) {
        return "CREATE " + (index.unique() ? "UNIQUE " : "")
                + "INDEX " + dialect.sqlName(SqlIdentifier.unquoted(index.name()))
                + " ON " + model.sqlTableName(dialect) + " (" + renderIndexColumns(index.columnList()) + ")";
    }

    protected String createUniqueIndexDdl(EntityModel model, TableUniqueConstraint constraint) {
        return "CREATE UNIQUE INDEX " + dialect.sqlName(SqlIdentifier.unquoted(constraint.name()))
                + " ON " + model.sqlTableName(dialect) + " (" + renderColumns(constraint.columnNames()) + ")";
    }

    protected String uniqueConstraintDefinition(TableUniqueConstraint constraint) {
        return "CONSTRAINT " + dialect.sqlName(SqlIdentifier.unquoted(constraint.name()))
                + " UNIQUE (" + renderColumns(constraint.columnNames()) + ")";
    }

    protected String checkConstraintDefinition(TableCheck check) {
        return "CONSTRAINT " + dialect.sqlName(SqlIdentifier.unquoted(check.name()))
                + " CHECK (" + check.constraints() + ")";
    }

    protected String renderColumns(List<String> columnNames) {
        List<String> rendered = new ArrayList<>();
        for (String column : columnNames) {
            rendered.add(dialect.sqlName(SqlIdentifier.parse(column)));
        }
        return String.join(", ", rendered);
    }

    protected String renderIndexColumns(List<String> columnNames) {
        List<String> rendered = new ArrayList<>();
        for (String column : columnNames) {
            String[] parts = column.split("\\s+");
            String renderedColumn = dialect.sqlName(SqlIdentifier.parse(parts[0]));
            rendered.add(parts.length == 1 ? renderedColumn : renderedColumn + " " + parts[1]);
        }
        return String.join(", ", rendered);
    }

    protected String inlineColumnComment(EntityField field) {
        return "";
    }

    protected void applyComments(Connection connection, EntityModel model) throws SQLException {
        List<String> statements = commentDdl(model);
        if (statements.isEmpty()) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            for (String statement : statements) {
                executeSql(st, statement);
            }
        }
    }

    protected List<String> commentDdl(EntityModel model) {
        return List.of();
    }

    protected String sqlStringLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    protected String dropTableDdl(EntityModel model) {
        return "DROP TABLE " + model.sqlTableName(dialect);
    }

    private static void requirePhysicalMutableTable(EntityModel model, String operation) {
        if (model.immutable()) {
            throw new MicroOrmException("Entity " + model.entityClass().getName() + " is immutable; schema operation is not allowed: " + operation);
        }
    }

    protected void validateAddJoinColumn(EntityModel model, io.github.connellite.microorm.mapping.ManyToOneField relation) {
        if (!relation.nullable()) {
            throw new MicroOrmException("Cannot add NOT NULL join column to existing table: "
                    + model.tableName() + "." + relation.joinColumn());
        }
    }

    protected void validateAddColumn(EntityModel model, EntityField f) {
        if (f.id()) {
            throw new MicroOrmException("Cannot add missing primary key column to existing table: "
                    + model.tableName() + "." + f.columnName());
        }
        if (!f.nullable() && f.columnDefault().isBlank()) {
            throw new MicroOrmException("Cannot add NOT NULL column without a default to existing table: "
                    + model.tableName() + "." + f.columnName());
        }
        if (f.unique()) {
            throw new MicroOrmException("Cannot add UNIQUE column to existing table: "
                    + model.tableName() + "." + f.columnName());
        }
    }

    protected String indexName(EntityModel model, EntityField field) {
        return INDEX_NAME_SANITIZER.matcher("idx_" + model.tableName() + "_" + field.columnName()).replaceAll("_");
    }

    protected Set<String> caseInsensitiveNullSkippingSet() {
        return Collections.newSetFromMap(new DelegatingNullSkippingMap<>(new CaseInsensitiveHashMap<>()));
    }

    protected abstract String autoIncrementPrimaryKeyDefinition(EntityField field);

    private static class LogHolder {
        private static final Logger logger = LoggerFactory.getLogger(AbstractSchemaManager.class);
    }
}
