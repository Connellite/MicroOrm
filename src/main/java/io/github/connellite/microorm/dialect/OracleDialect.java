package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.schema.OracleSchemaManager;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.sql.OracleSqlGenerator;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.OracleJdbcValueMapper;

import java.sql.Connection;
import java.sql.SQLException;

public final class OracleDialect extends AbstractDialect {

    public static final OracleDialect INSTANCE = new OracleDialect();

    private final JdbcValueMapper valueMapper = new OracleJdbcValueMapper();
    private final SqlGenerator sqlGenerator = new OracleSqlGenerator(this);
    private final SchemaManager schemaManager = new OracleSchemaManager(this);

    private OracleDialect() {
    }

    @Override
    protected String quotePreserveCase(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    protected String unquotedCatalogName(String identifier) {
        return upper(identifier);
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
