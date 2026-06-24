package io.github.connellite.stoneorm.dialect;

import io.github.connellite.stoneorm.mapping.EntityModel;
import io.github.connellite.stoneorm.schema.PostgresSchemaManager;
import io.github.connellite.stoneorm.schema.SchemaManager;
import io.github.connellite.stoneorm.sql.PostgresSqlGenerator;
import io.github.connellite.stoneorm.sql.SqlGenerator;
import io.github.connellite.stoneorm.type.DefaultJdbcValueMapper;
import io.github.connellite.stoneorm.type.JdbcValueMapper;
import io.github.connellite.stoneorm.type.UuidStorage;

import java.sql.Connection;
import java.sql.SQLException;

public final class PostgresDialect implements Dialect {

    public static final PostgresDialect INSTANCE = new PostgresDialect();

    private final JdbcValueMapper valueMapper = new DefaultJdbcValueMapper(UuidStorage.NATIVE);
    private final SqlGenerator sqlGenerator = new PostgresSqlGenerator(this);
    private final SchemaManager schemaManager = new PostgresSchemaManager(this);

    private PostgresDialect() {
    }

    @Override
    public String quote(String identifier) {
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
