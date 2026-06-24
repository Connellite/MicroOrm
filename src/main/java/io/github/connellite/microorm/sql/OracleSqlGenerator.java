package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.dialect.Dialect;

public final class OracleSqlGenerator extends AbstractSqlGenerator {

    public OracleSqlGenerator(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String limitOne(String sql) {
        return sql + " FETCH FIRST 1 ROWS ONLY";
    }
}
