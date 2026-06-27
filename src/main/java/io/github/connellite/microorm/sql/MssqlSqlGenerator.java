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

    @Override
    protected String applyLimitOffset(String sql, Integer limit, Integer offset, boolean hasOrder) {
        int effectiveOffset = offset == null ? 0 : offset;
        if (limit == null && effectiveOffset == 0) {
            return sql;
        }
        if (effectiveOffset == 0 && limit != null && sql.startsWith("SELECT ")) {
            return "SELECT TOP " + limit + " " + sql.substring("SELECT ".length());
        }
        if (!hasOrder) {
            sql += " ORDER BY (SELECT 1)";
        }
        sql += " OFFSET " + effectiveOffset + " ROWS";
        if (limit != null) {
            sql += " FETCH NEXT " + limit + " ROWS ONLY";
        }
        return sql;
    }
}
