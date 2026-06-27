package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.sql.BoundStatement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared CRUD SQL generation for runtime tables. Subclasses supply dialect-specific {@code LIMIT 1} / {@code TOP 1} syntax.
 */
public abstract class AbstractDynamicSqlGenerator implements DynamicSqlGenerator {

    private final Dialect dialect;
    private final DynamicValueBinder valueBinder;

    protected AbstractDynamicSqlGenerator(Dialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.valueBinder = new DynamicValueBinder(dialect);
    }

    @Override
    public BoundStatement insert(DynamicTable table, Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> colQuoted = new ArrayList<>();
        List<String> slots = new ArrayList<>();
        for (Column column : table.columns()) {
            if (column.autoIncrement() && isUnsetAutoIncrement(values.get(column.name()))) {
                continue;
            }
            Object raw = values.get(column.name());
            if (!values.containsKey(column.name())) {
                if (!column.nullable()) {
                    throw new MicroOrmException("Missing required column '" + column.name() + "'");
                }
                raw = null;
            }
            colQuoted.add(dialect.sqlName(column.columnIdentifier()));
            slots.add(":" + column.name());
            params.put(column.name(), valueBinder.toJdbc(column, raw));
        }
        if (colQuoted.isEmpty()) {
            throw new MicroOrmException("Insert requires at least one column value for table '" + table.name() + "'");
        }
        String sql = "INSERT INTO " + dialect.sqlName(table.tableIdentifier()) + " ("
                + String.join(", ", colQuoted) + ") VALUES (" + String.join(", ", slots) + ")";
        return BoundStatement.of(sql, params);
    }

    @Override
    public BoundStatement update(DynamicTable table, Map<String, ?> setValues, Map<String, ?> whereValues) {
        Objects.requireNonNull(setValues, "setValues");
        Objects.requireNonNull(whereValues, "whereValues");
        if (setValues.isEmpty()) {
            throw new MicroOrmException("Update requires at least one SET column for table '" + table.name() + "'");
        }
        if (whereValues.isEmpty()) {
            throw new MicroOrmException("Update requires at least one WHERE column for table '" + table.name() + "'");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> sets = new ArrayList<>();
        for (Map.Entry<String, ?> entry : setValues.entrySet()) {
            Column column = table.columnByName(entry.getKey());
            if (column.primaryKey()) {
                throw new MicroOrmException("Primary key cannot appear in SET clause: " + column.name());
            }
            sets.add(dialect.sqlName(column.columnIdentifier()) + " = :" + paramName("set", column.name()));
            params.put(paramName("set", column.name()), valueBinder.toJdbc(column, entry.getValue()));
        }
        List<String> predicates = buildPredicates(table, whereValues, params, "where");
        String sql = "UPDATE " + dialect.sqlName(table.tableIdentifier()) + " SET " + String.join(", ", sets)
                + " WHERE " + String.join(" AND ", predicates);
        return BoundStatement.of(sql, params);
    }

    @Override
    public BoundStatement delete(DynamicTable table, Map<String, ?> whereValues) {
        Objects.requireNonNull(whereValues, "whereValues");
        if (whereValues.isEmpty()) {
            throw new MicroOrmException("Delete requires at least one WHERE column for table '" + table.name() + "'");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> predicates = buildPredicates(table, whereValues, params, "where");
        String sql = "DELETE FROM " + dialect.sqlName(table.tableIdentifier())
                + " WHERE " + String.join(" AND ", predicates);
        return BoundStatement.of(sql, params);
    }

    @Override
    public BoundStatement selectAll(DynamicTable table) {
        return BoundStatement.of(selectAllSql(table), Map.of());
    }

    @Override
    public BoundStatement selectWhere(DynamicTable table, Map<String, ?> filters) {
        if (filters == null || filters.isEmpty()) {
            return selectAll(table);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> predicates = buildPredicates(table, filters, params, "filter");
        return BoundStatement.of(selectAllSql(table) + " WHERE " + String.join(" AND ", predicates), params);
    }

    @Override
    public BoundStatement exists(DynamicTable table, Map<String, ?> whereValues) {
        Objects.requireNonNull(whereValues, "whereValues");
        if (whereValues.isEmpty()) {
            throw new MicroOrmException("Exists requires at least one WHERE column for table '" + table.name() + "'");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> predicates = buildPredicates(table, whereValues, params, "where");
        String sql = "SELECT 1 FROM " + dialect.sqlName(table.tableIdentifier())
                + " WHERE " + String.join(" AND ", predicates);
        return BoundStatement.of(limitOne(sql), params);
    }

    protected abstract String limitOne(String sql);

    protected final Dialect dialect() {
        return dialect;
    }

    protected final DynamicValueBinder valueBinder() {
        return valueBinder;
    }

    private String selectAllSql(DynamicTable table) {
        List<String> cols = new ArrayList<>();
        for (Column column : table.columns()) {
            cols.add(dialect.sqlName(table.tableIdentifier()) + "." + dialect.sqlName(column.columnIdentifier()));
        }
        return "SELECT " + String.join(", ", cols) + " FROM " + dialect.sqlName(table.tableIdentifier());
    }

    private List<String> buildPredicates(
            DynamicTable table,
            Map<String, ?> values,
            Map<String, Object> params,
            String prefix) {
        List<String> predicates = new ArrayList<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Column column = table.columnByName(entry.getKey());
            String param = paramName(prefix, column.name());
            if (params.containsKey(param)) {
                throw new MicroOrmException("Duplicate parameter for column: " + column.name());
            }
            predicates.add(dialect.sqlName(column.columnIdentifier()) + " = :" + param);
            params.put(param, valueBinder.toJdbc(column, entry.getValue()));
        }
        return predicates;
    }

    private static boolean isUnsetAutoIncrement(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number number) {
            return number.longValue() == 0L;
        }
        return false;
    }

    private static String paramName(String prefix, String columnName) {
        return prefix + "_" + columnName;
    }
}
