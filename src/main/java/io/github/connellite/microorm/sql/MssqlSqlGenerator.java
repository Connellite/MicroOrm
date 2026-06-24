package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.dialect.Dialect;

public final class MssqlSqlGenerator extends AbstractSqlGenerator {

    public MssqlSqlGenerator(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String limitOne(String sql) {
        if (sql.startsWith("SELECT 1 FROM ")) {
            return "SELECT TOP 1 1 FROM " + sql.substring("SELECT 1 FROM ".length());
        }
        return sql;
    }
}
