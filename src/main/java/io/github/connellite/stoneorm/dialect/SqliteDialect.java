package io.github.connellite.stoneorm.dialect;

import io.github.connellite.stoneorm.mapping.EntityModel;
import io.github.connellite.stoneorm.schema.SchemaManager;
import io.github.connellite.stoneorm.schema.SqliteSchemaManager;
import io.github.connellite.stoneorm.sql.SqlGenerator;
import io.github.connellite.stoneorm.sql.SqliteSqlGenerator;

import java.sql.Connection;
import java.sql.SQLException;

/** SQLite 3 — identifiers quoted as "name". */
public final class SqliteDialect implements Dialect {

    public static final SqliteDialect INSTANCE = new SqliteDialect();

    private final SqlGenerator sqlGenerator = new SqliteSqlGenerator(this);
    private final SchemaManager schemaManager = new SqliteSchemaManager(this);

    private SqliteDialect() {
    }

    @Override
    public String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    @Override
    public SqlGenerator sqlGenerator() {
        return sqlGenerator;
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
