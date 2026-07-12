package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.dialect.Dialect;

public final class PostgresSqlGenerator extends AbstractSqlGenerator {

    public PostgresSqlGenerator(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String limitOne(String sql) {
        return sql + " LIMIT 1";
    }

    @Override
    protected String applyLimitOffset(String sql, Integer limit, Integer offset, boolean hasOrder) {
        if (limit == null && (offset == null || offset == 0)) {
            return sql;
        }
        if (limit != null) {
            sql += " LIMIT " + limit;
        }
        if (offset != null && offset > 0) {
            sql += " OFFSET " + offset;
        }
        return sql;
    }
}
