package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.dialect.Dialect;

/** Dynamic SQL for dialects that use {@code LIMIT}/{@code OFFSET}. */
public final class DefaultDynamicSqlGenerator extends AbstractDynamicSqlGenerator {

    public DefaultDynamicSqlGenerator(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String limitOne(String sql) {
        return sql + " LIMIT 1";
    }
}
