package io.github.connellite.microorm.type;

import io.github.connellite.microorm.dialect.Dialect;

/**
 * Dialect-specific SQL type for a mapped field. The Java type stays unchanged.
 */
public interface JdbcType {

    /**
     * SQL column type for {@code dialect}, for example {@code jsonb} or {@code NVARCHAR(MAX)}.
     */
    String sqlType(Dialect dialect);

    /**
     * JDBC value to bind after attribute conversion. The default returns {@code value} unchanged.
     */
    default Object toJdbc(Dialect dialect, Object value) {
        return value;
    }
}
