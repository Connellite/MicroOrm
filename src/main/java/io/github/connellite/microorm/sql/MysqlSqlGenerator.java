package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.query.JoinType;

public final class MysqlSqlGenerator extends AbstractSqlGenerator {

    public MysqlSqlGenerator(Dialect dialect) {
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
        } else if (offset != null && offset > 0) {
            sql += " LIMIT 18446744073709551615";
        }
        if (offset != null && offset > 0) {
            sql += " OFFSET " + offset;
        }
        return sql;
    }

    @Override
    protected boolean supportsJoinType(JoinType joinType) {
        return joinType != JoinType.FULL;
    }
}
