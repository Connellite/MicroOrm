package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.dialect.Dialect;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps {@link java.sql.ResultSet} rows to {@link Map} instances keyed by {@link Column#name()}.
 */
public final class MapRowMapper {

    private MapRowMapper() {
    }

    /**
     * Reads one row into a map using the table's column descriptors and dialect column labels.
     *
     * @return map keyed by logical column names
     */
    public static Map<String, Object> mapRow(
            ResultSet rs,
            DynamicTable table,
            Dialect dialect,
            DynamicValueBinder binder) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Column column : table.columns()) {
            String label = dialect.jdbcColumnLabel(column.columnIdentifier());
            Object value = binder.readJdbc(column, rs, label);
            if (value == null && rs.wasNull()) {
                row.put(column.name(), null);
            } else {
                row.put(column.name(), binder.fromJdbc(column, value));
            }
        }
        return row;
    }
}
