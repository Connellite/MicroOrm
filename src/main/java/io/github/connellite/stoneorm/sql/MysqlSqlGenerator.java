package io.github.connellite.stoneorm.sql;

import io.github.connellite.stoneorm.dialect.Dialect;

public final class MysqlSqlGenerator extends AbstractSqlGenerator {

    public MysqlSqlGenerator(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String limitOne(String sql) {
        return sql + " LIMIT 1";
    }
}
