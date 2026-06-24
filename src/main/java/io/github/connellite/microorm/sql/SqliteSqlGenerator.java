package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.dialect.Dialect;

public final class SqliteSqlGenerator extends AbstractSqlGenerator {

    public SqliteSqlGenerator(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String limitOne(String sql) {
        return sql + " LIMIT 1";
    }
}
