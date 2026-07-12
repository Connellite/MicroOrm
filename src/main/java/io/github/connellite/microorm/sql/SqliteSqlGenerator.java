package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.query.JoinType;

public final class SqliteSqlGenerator extends AbstractSqlGenerator {

    public SqliteSqlGenerator(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String limitOne(String sql) {
        return sql + " LIMIT 1";
    }

    @Override
    protected boolean supportsJoinType(JoinType joinType) {
        return joinType != JoinType.RIGHT && joinType != JoinType.FULL;
    }
}
