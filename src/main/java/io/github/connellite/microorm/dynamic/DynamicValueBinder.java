package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.type.JdbcValueMapper;

import java.util.Objects;

/**
 * Converts Java values for dynamic columns to JDBC parameters and back, reusing the active {@link Dialect}'s
 * {@link JdbcValueMapper} (UUID encoding, Oracle booleans, etc.).
 */
public final class DynamicValueBinder {

    private final JdbcValueMapper mapper;

    /** Creates a binder that uses the given dialect's value mapper. */
    public DynamicValueBinder(Dialect dialect) {
        Objects.requireNonNull(dialect, "dialect");
        this.mapper = dialect.valueMapper();
    }

    /** Converts a Java value to a JDBC parameter for the given column. */
    public Object toJdbc(Column column, Object value) {
        if (value == null) {
            return null;
        }
        return mapper.toJdbcValue(ValueBinderFields.field(column.type()), value);
    }

    /** Converts a JDBC value read from a {@link java.sql.ResultSet} to the column's Java type. */
    public Object fromJdbc(Column column, Object value) {
        if (value == null) {
            return null;
        }
        return mapper.fromJdbcValue(ValueBinderFields.field(column.type()), value);
    }
}
