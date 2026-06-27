package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.schema.SqliteSchemaManager;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqliteSqlGenerator;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;

import java.sql.Connection;
import java.sql.SQLException;

/** SQLite 3 — unquoted identifiers are case-insensitive; backticks request quoted SQL. */
public final class SqliteDialect extends AbstractDialect {

    private final JdbcValueMapper valueMapper = new DefaultJdbcValueMapper(UuidStorage.STRING);
    private final SqlGenerator sqlGenerator = new SqliteSqlGenerator(this);
    private final SchemaManager schemaManager = new SqliteSchemaManager(this);

    private SqliteDialect() {
    }

    public static SqliteDialect getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final SqliteDialect INSTANCE = new SqliteDialect();
    }

    @Override
    protected String quotePreserveCase(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public SqlGenerator sqlGenerator() {
        return sqlGenerator;
    }

    @Override
    public JdbcValueMapper valueMapper() {
        return valueMapper;
    }

    @Override
    public void createTable(Connection c, EntityModel model) throws SQLException {
        schemaManager.createTable(c, model);
    }

    @Override
    public void syncTable(Connection c, EntityModel model) throws SQLException {
        schemaManager.syncTable(c, model);
    }

    @Override
    public void dropTable(Connection c, EntityModel model) throws SQLException {
        schemaManager.dropTable(c, model);
    }
}
