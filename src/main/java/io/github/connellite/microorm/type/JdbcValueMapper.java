package io.github.connellite.microorm.type;

import io.github.connellite.microorm.mapping.EntityField;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts Java entity field values to JDBC parameters and back.
 * Each {@link io.github.connellite.microorm.dialect.Dialect} supplies an implementation
 * (UUID as native/binary/string, Oracle booleans as numbers, etc.).
 */
public interface JdbcValueMapper {

    /** Converts a non-null Java value to a JDBC parameter (may return {@code null} for SQL NULL input). */
    Object toJdbcValue(EntityField field, Object value);

    /** Converts a JDBC value read from a {@link java.sql.ResultSet} to the field's Java type. */
    Object fromJdbcValue(EntityField field, Object value);

    /** Reads a raw JDBC value for the field. Dialects may override this for driver-specific ResultSet objects. */
    default Object readJdbcValue(EntityField field, ResultSet rs, String columnLabel) throws SQLException {
        return rs.getObject(columnLabel);
    }

    /** UUID storage strategy expected by this mapper, or {@code null} when it is not declared. */
    default UuidStorage uuidStorage() {
        return null;
    }
}
