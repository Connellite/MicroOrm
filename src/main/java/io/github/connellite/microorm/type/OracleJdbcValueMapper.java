package io.github.connellite.microorm.type;

import io.github.connellite.microorm.mapping.EntityField;

/**
 * Oracle-specific {@link JdbcValueMapper}: {@code boolean} columns read/written as {@code 0}/{@code 1}.
 */
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
        if (value != null && (field.javaType() == boolean.class || field.javaType() == Boolean.class)) {
            if (value instanceof Number n) {
                return n.intValue() != 0;
            }
        }
        return delegate.fromJdbcValue(field, value);
    }
}
