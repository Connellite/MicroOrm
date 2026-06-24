package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.dialect.Dialect;

public final class MssqlSqlGenerator extends AbstractSqlGenerator {

    public MssqlSqlGenerator(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String limitOne(String sql) {
        return sql.replaceFirst("SELECT 1", "SELECT TOP 1 1");
    }
}
