package io.github.connellite.microorm.type;

/**
 * JDBC parameter bound with an explicit {@link java.sql.Types} code.
 */
public record JdbcTypedValue(Object value, int sqlType) {
}
