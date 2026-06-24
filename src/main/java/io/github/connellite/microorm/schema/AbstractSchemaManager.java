package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

public abstract class AbstractSchemaManager implements SchemaManager {

    protected final Dialect dialect;

    protected AbstractSchemaManager(Dialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public void createTable(Connection connection, EntityModel model) throws SQLException {
        if (!existingColumns(connection, model).isEmpty()) {
            createIndexes(connection, model);
            return;
        }
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
                if (existingColumns.contains(normalize(f.columnName()))) {
                    continue;
                }
                validateAddColumn(model, f);
                st.execute("ALTER TABLE " + dialect.quote(model.tableName()) + " ADD " + columnDefinition(f, false));
            }
        }
        createIndexes(connection, model);
    }

    @Override
    public void dropTable(Connection connection, EntityModel model) throws SQLException {
        if (existingColumns(connection, model).isEmpty()) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute(dropTableDdl(model));
        }
    }

    protected String buildCreateTableDdl(EntityModel model) {
        StringJoiner columns = new StringJoiner(", ");
        for (EntityField f : model.fields()) {
            columns.add(columnDefinition(f, true));
        }
        return "CREATE TABLE " + dialect.quote(model.tableName()) + " (" + columns + ")";
    }

    protected String columnDefinition(EntityField f, boolean includeUnique) {
        StringBuilder sb = new StringBuilder();
        sb.append(dialect.quote(f.columnName())).append(' ');
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
        return sb.toString();
    }

    protected Set<String> existingColumns(Connection connection, EntityModel model) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, model.tableName(), null)) {
            while (rs.next()) {
                columns.add(normalize(rs.getString("COLUMN_NAME")));
            }
        }
        if (columns.isEmpty()) {
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, model.tableName().toUpperCase(Locale.ROOT), null)) {
                while (rs.next()) {
                    columns.add(normalize(rs.getString("COLUMN_NAME")));
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
                st.execute(createIndexDdl(model, f));
            }
        }
    }

    protected boolean indexExists(Connection connection, EntityModel model, EntityField field) throws SQLException {
        String indexName = indexName(model, field);
        if (findIndex(connection, model.tableName(), indexName)) {
            return true;
        }
        return findIndex(connection, model.tableName().toUpperCase(Locale.ROOT), indexName);
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

    protected String createIndexDdl(EntityModel model, EntityField field) {
        String indexName = indexName(model, field);
        return "CREATE INDEX " + dialect.quote(indexName)
                + " ON " + dialect.quote(model.tableName()) + " (" + dialect.quote(field.columnName()) + ")";
    }

    protected String dropTableDdl(EntityModel model) {
        return "DROP TABLE " + dialect.quote(model.tableName());
    }

    protected void validateAddColumn(EntityModel model, EntityField f) {
        if (f.id()) {
            throw new MicroOrmException("Cannot add missing primary key column to existing table: "
                    + model.tableName() + "." + f.columnName());
        }
        if (!f.nullable()) {
            throw new MicroOrmException("Cannot add NOT NULL column without a default to existing table: "
                    + model.tableName() + "." + f.columnName());
        }
        if (f.unique()) {
            throw new MicroOrmException("Cannot add UNIQUE column to existing table: "
                    + model.tableName() + "." + f.columnName());
        }
    }

    protected String indexName(EntityModel model, EntityField field) {
        return ("idx_" + model.tableName() + "_" + field.columnName()).replaceAll("[^a-zA-Z0-9_]", "_");
    }

    protected String normalize(String identifier) {
        return identifier == null ? "" : identifier.toLowerCase(Locale.ROOT);
    }

    protected abstract String baseType(EntityField field);

    protected abstract String autoIncrementPrimaryKeyDefinition(EntityField field);
}
