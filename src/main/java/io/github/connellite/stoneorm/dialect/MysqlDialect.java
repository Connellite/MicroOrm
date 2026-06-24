package io.github.connellite.stoneorm.dialect;

import io.github.connellite.stoneorm.mapping.EntityModel;
import io.github.connellite.stoneorm.schema.MysqlSchemaManager;
import io.github.connellite.stoneorm.schema.SchemaManager;
import io.github.connellite.stoneorm.sql.MysqlSqlGenerator;
import io.github.connellite.stoneorm.sql.SqlGenerator;
import io.github.connellite.stoneorm.type.DefaultJdbcValueMapper;
import io.github.connellite.stoneorm.type.JdbcValueMapper;
import io.github.connellite.stoneorm.type.UuidStorage;

import java.sql.Connection;
import java.sql.SQLException;

public final class MysqlDialect implements Dialect {

    public static final MysqlDialect INSTANCE = new MysqlDialect();

    private final JdbcValueMapper valueMapper = new DefaultJdbcValueMapper(UuidStorage.BINARY);
    private final SqlGenerator sqlGenerator = new MysqlSqlGenerator(this);
    private final SchemaManager schemaManager = new MysqlSchemaManager(this);

    private MysqlDialect() {
    }

    @Override
    public String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
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
