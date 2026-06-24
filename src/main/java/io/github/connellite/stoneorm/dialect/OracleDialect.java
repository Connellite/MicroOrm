package io.github.connellite.stoneorm.dialect;

import io.github.connellite.stoneorm.mapping.EntityModel;
import io.github.connellite.stoneorm.schema.OracleSchemaManager;
import io.github.connellite.stoneorm.schema.SchemaManager;
import io.github.connellite.stoneorm.sql.OracleSqlGenerator;
import io.github.connellite.stoneorm.sql.SqlGenerator;
import io.github.connellite.stoneorm.type.JdbcValueMapper;
import io.github.connellite.stoneorm.type.OracleJdbcValueMapper;

import java.sql.Connection;
import java.sql.SQLException;

public final class OracleDialect implements Dialect {

    public static final OracleDialect INSTANCE = new OracleDialect();

    private final JdbcValueMapper valueMapper = new OracleJdbcValueMapper();
    private final SqlGenerator sqlGenerator = new OracleSqlGenerator(this);
    private final SchemaManager schemaManager = new OracleSchemaManager(this);

    private OracleDialect() {
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
