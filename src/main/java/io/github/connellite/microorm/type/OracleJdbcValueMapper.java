package io.github.connellite.microorm.type;

import io.github.connellite.microorm.mapping.EntityField;

import java.sql.ResultSet;
import java.sql.SQLException;

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

    @Override
    public Object readJdbcValue(EntityField field, ResultSet rs, String columnLabel) throws SQLException {
        int columnIndex = rs.findColumn(columnLabel);
        return normalizeResultSetValue(rs, columnLabel, columnIndex, rs.getObject(columnLabel));
    }

    @Override
    public UuidStorage uuidStorage() {
        return delegate.uuidStorage();
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
