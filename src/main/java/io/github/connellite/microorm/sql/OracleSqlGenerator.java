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

    @Override
    protected String applyLimitOffset(String sql, Integer limit, Integer offset, boolean hasOrder) {
        int effectiveOffset = offset == null ? 0 : offset;
        if (limit == null && effectiveOffset == 0) {
            return sql;
        }
        if (effectiveOffset > 0) {
            sql += " OFFSET " + effectiveOffset + " ROWS";
            if (limit != null) {
                sql += " FETCH NEXT " + limit + " ROWS ONLY";
            }
            return sql;
        }
        return sql + " FETCH FIRST " + limit + " ROWS ONLY";
    }
}
