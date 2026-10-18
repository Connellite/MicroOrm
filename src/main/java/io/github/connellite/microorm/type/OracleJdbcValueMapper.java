package io.github.connellite.microorm.type;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.mapping.EntityField;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Oracle-specific {@link JdbcValueMapper}: {@code boolean} columns read/written as {@code 0}/{@code 1}.
 */
public final class OracleJdbcValueMapper implements JdbcValueMapper {

    private final Dialect dialect;
    private final DefaultJdbcValueMapper delegate;

    public OracleJdbcValueMapper() {
        this(null);
    }

    public OracleJdbcValueMapper(Dialect dialect) {
        this.dialect = dialect;
        this.delegate = new DefaultJdbcValueMapper(UuidStorage.BINARY, dialect);
    }

    @Override
    public Object toJdbcValue(EntityField field, Object value) {
        value = field.convertToDatabaseColumn(value);
        if (value != null && (field.jdbcJavaType() == boolean.class || field.jdbcJavaType() == Boolean.class)) {
            return applyJdbcType(field, Boolean.TRUE.equals(value) ? 1 : 0);
        }
        return delegate.toJdbcValue(field, value, false);
    }

    @Override
    public Object fromJdbcValue(EntityField field, Object value) {
        if (value != null && (field.jdbcJavaType() == boolean.class || field.jdbcJavaType() == Boolean.class)) {
            if (value instanceof Number n) {
                return field.convertToEntityAttribute(n.intValue() != 0);
            }
        }
        return delegate.fromJdbcValue(field, value);
    }

    @Override
    public Object readJdbcValue(EntityField field, ResultSet rs, String columnLabel) throws SQLException {
        int columnIndex = rs.findColumn(columnLabel);
        return normalizeResultSetValue(rs, columnLabel, columnIndex, rs.getObject(columnLabel));
    }

    @Override
    public UuidStorage uuidStorage() {
        return delegate.uuidStorage();
    }

    private Object applyJdbcType(EntityField field, Object value) {
        if (value == null || field.jdbcType() == null) {
            return value;
        }
        return field.jdbcType().toJdbc(dialect, value);
    }

    private static Object normalizeResultSetValue(
            ResultSet rs,
            String columnLabel,
            int columnIndex,
            Object obj) throws SQLException {
        if (obj == null) {
            return null;
        }
        String className = obj.getClass().getName();
        if ("oracle.sql.TIMESTAMP".equals(className)
                || "oracle.sql.TIMESTAMPTZ".equals(className)
                || "oracle.sql.TIMESTAMPLTZ".equals(className)) {
            return rs.getTimestamp(columnLabel);
        }
        if (className.startsWith("oracle.sql.DATE")) {
            String metaDataClassName = rs.getMetaData().getColumnClassName(columnIndex);
            if ("java.sql.Timestamp".equals(metaDataClassName) || "oracle.sql.TIMESTAMP".equals(metaDataClassName)) {
                return rs.getTimestamp(columnLabel);
            }
            return rs.getDate(columnLabel);
        }
        if (obj instanceof java.sql.Date
                && "java.sql.Timestamp".equals(rs.getMetaData().getColumnClassName(columnIndex))) {
            return rs.getTimestamp(columnLabel);
        }
        return obj;
    }
}
