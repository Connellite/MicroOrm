package io.github.connellite.microorm.type;

import io.github.connellite.microorm.mapping.EntityField;

public final class OracleJdbcValueMapper implements JdbcValueMapper {

    private final JdbcValueMapper delegate = new DefaultJdbcValueMapper(UuidStorage.BINARY);

    @Override
    public Object toJdbcValue(EntityField field, Object value) {
        if (value != null && (field.javaType() == boolean.class || field.javaType() == Boolean.class)) {
            return Boolean.TRUE.equals(value) ? 1 : 0;
        }
        return delegate.toJdbcValue(field, value);
    }

    @Override
    public Object fromJdbcValue(EntityField field, Object value) {
        return delegate.fromJdbcValue(field, value);
    }
}
