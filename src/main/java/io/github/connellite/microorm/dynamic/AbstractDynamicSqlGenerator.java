package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.query.ComparisonOperator;
import io.github.connellite.microorm.query.CompositeCriterion;
import io.github.connellite.microorm.query.Criterion;
import io.github.connellite.microorm.query.CriterionKind;
import io.github.connellite.microorm.query.ExistsCriterion;
import io.github.connellite.microorm.query.FieldCriterion;
import io.github.connellite.microorm.query.NotCriterion;
import io.github.connellite.microorm.query.Order;
import io.github.connellite.microorm.query.QuantifiedSubqueryCriterion;
import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;

import java.util.ArrayList;
import java.util.Collection;
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
    public BoundStatement select(DynamicTable table, DynamicSelect query) {
        Objects.requireNonNull(query, "query");
        requireMatchingTable(table, query.tableName(), "DynamicSelect");
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Collection<?>> collectionParams = new LinkedHashMap<>();
        int[] paramCounter = {1};
        String sql = selectSql(table, query.selectedColumns(), query.isDistinct());
        if (query.criterion() != null) {
            sql += " WHERE " + renderCriterion(table, query.criterion(), params, collectionParams, paramCounter);
        }
        if (!query.groupColumns().isEmpty()) {
            sql += " GROUP BY " + renderColumns(table, query.groupColumns());
        }
        if (query.havingCriterion() != null) {
            sql += " HAVING " + renderCriterion(table, query.havingCriterion(), params, collectionParams, paramCounter);
        }
        if (!query.orders().isEmpty()) {
            List<String> orderSql = new ArrayList<>();
            for (Order order : query.orders()) {
                Column column = resolveColumn(table, order.fieldName());
                orderSql.add(columnSql(table, column) + " " + order.direction().name());
            }
            sql += " ORDER BY " + String.join(", ", orderSql);
        }
        return BoundStatement.of(applyLimitOffset(
                sql,
                query.limit().isPresent() ? query.limit().getAsInt() : null,
                query.offset().isPresent() ? query.offset().getAsInt() : null,
                !query.orders().isEmpty()), params, collectionParams);
    }

    @Override
    public BoundStatement update(DynamicTable table, DynamicUpdate mutation) {
        Objects.requireNonNull(mutation, "mutation");
        requireMatchingTable(table, mutation.tableName(), "DynamicUpdate");
        if (mutation.assignments().isEmpty()) {
            throw new MicroOrmException("DynamicUpdate requires at least one assignment: " + table.name());
        }
        requireWhereOrAllRows(mutation.criterion(), mutation.isAllRows(), "DynamicUpdate");

        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Collection<?>> collectionParams = new LinkedHashMap<>();
        List<String> sets = new ArrayList<>();
        for (Map.Entry<String, Object> entry : mutation.assignments().entrySet()) {
            Column column = resolveRootColumn(table, entry.getKey(), "update");
            if (column.primaryKey()) {
                throw new MicroOrmException("DynamicUpdate cannot assign primary key column: " + entry.getKey());
            }
            String param = paramName("set", column.name());
            if (params.containsKey(param)) {
                throw new MicroOrmException("Duplicate update assignment for column: " + entry.getKey());
            }
            sets.add(dialect.sqlName(column.columnIdentifier()) + " = :" + param);
            params.put(param, valueBinder.toJdbc(column, entry.getValue()));
        }

        String sql = "UPDATE " + dialect.sqlName(table.tableIdentifier()) + " SET " + String.join(", ", sets);
        if (mutation.criterion() != null) {
            sql += " WHERE " + renderCriterion(table, mutation.criterion(), params, collectionParams, new int[]{1});
        }
        return BoundStatement.of(sql, params, collectionParams);
    }

    @Override
    public BoundStatement delete(DynamicTable table, DynamicDelete mutation) {
        Objects.requireNonNull(mutation, "mutation");
        requireMatchingTable(table, mutation.tableName(), "DynamicDelete");
        requireWhereOrAllRows(mutation.criterion(), mutation.isAllRows(), "DynamicDelete");

        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Collection<?>> collectionParams = new LinkedHashMap<>();
        String sql = "DELETE FROM " + dialect.sqlName(table.tableIdentifier());
        if (mutation.criterion() != null) {
            sql += " WHERE " + renderCriterion(table, mutation.criterion(), params, collectionParams, new int[]{1});
        }
        return BoundStatement.of(sql, params, collectionParams);
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

    protected String applyLimitOffset(String sql, Integer limit, Integer offset, boolean hasOrder) {
        if (limit == null && (offset == null || offset == 0)) {
            return sql;
        }
        if (limit != null) {
            sql += " LIMIT " + limit;
        }
        if (offset != null && offset > 0) {
            if (limit == null) {
                sql += " LIMIT -1";
            }
            sql += " OFFSET " + offset;
        }
        return sql;
    }

    protected final Dialect dialect() {
        return dialect;
    }

    protected final DynamicValueBinder valueBinder() {
        return valueBinder;
    }

    private String selectAllSql(DynamicTable table) {
        return selectSql(table, List.of(), false);
    }

    private String selectSql(DynamicTable table, List<String> selectedColumns, boolean distinct) {
        List<String> cols = new ArrayList<>();
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            for (Column column : table.columns()) {
                cols.add(columnSql(table, column));
            }
        } else {
            for (String columnName : selectedColumns) {
                cols.add(columnSql(table, resolveRootColumn(table, columnName, "select")));
            }
        }
        return "SELECT " + (distinct ? "DISTINCT " : "")
                + String.join(", ", cols) + " FROM " + dialect.sqlName(table.tableIdentifier());
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

    private String renderCriterion(
            DynamicTable table,
            Criterion criterion,
            Map<String, Object> params,
            Map<String, Collection<?>> collectionParams,
            int[] paramCounter) {
        if (criterion instanceof FieldCriterion fieldCriterion) {
            return renderFieldCriterion(table, fieldCriterion, params, collectionParams, paramCounter);
        }
        if (criterion instanceof CompositeCriterion composite) {
            String separator = " " + composite.operator().name() + " ";
            List<String> rendered = new ArrayList<>();
            for (Criterion child : composite.criteria()) {
                rendered.add(renderCriterion(table, child, params, collectionParams, paramCounter));
            }
            return "(" + String.join(separator, rendered) + ")";
        }
        if (criterion instanceof NotCriterion notCriterion) {
            return "NOT (" + renderCriterion(table, notCriterion.criterion(), params, collectionParams, paramCounter) + ")";
        }
        if (criterion instanceof ExistsCriterion existsCriterion) {
            if (existsCriterion.entitySelect() != null) {
                throw new MicroOrmException("Dynamic criteria do not support EntitySelect subqueries");
            }
            mergeSubqueryParameters(existsCriterion.query(), params, collectionParams);
            return (existsCriterion.negated() ? "NOT EXISTS" : "EXISTS")
                    + " (" + existsCriterion.query().sql() + ")";
        }
        if (criterion instanceof QuantifiedSubqueryCriterion quantified) {
            if (quantified.entitySelect() != null) {
                throw new MicroOrmException("Dynamic criteria do not support EntitySelect subqueries");
            }
            Column column = resolveColumn(table, quantified.fieldName());
            mergeSubqueryParameters(quantified.query(), params, collectionParams);
            return columnSql(table, column) + " " + quantified.operator().sql() + " "
                    + quantified.quantifier().name() + " (" + quantified.query().sql() + ")";
        }
        throw new MicroOrmException("Unsupported criterion type: " + criterion.getClass().getName());
    }

    private String renderFieldCriterion(
            DynamicTable table,
            FieldCriterion criterion,
            Map<String, Object> params,
            Map<String, Collection<?>> collectionParams,
            int[] paramCounter) {
        Column column = resolveColumn(table, criterion.fieldName());
        String columnSql = columnSql(table, column);
        return switch (criterion.kind()) {
            case COMPARISON -> renderComparison(column, columnSql, criterion, params, paramCounter);
            case IN, NOT_IN -> renderIn(column, columnSql, criterion, collectionParams, paramCounter);
            case LIKE -> {
                String param = nextParam(paramCounter);
                params.put(param, valueBinder.toJdbc(column, criterion.value()));
                yield columnSql + " LIKE :" + param;
            }
            case NOT_LIKE -> {
                String param = nextParam(paramCounter);
                params.put(param, valueBinder.toJdbc(column, criterion.value()));
                yield columnSql + " NOT LIKE :" + param;
            }
            case BETWEEN, NOT_BETWEEN -> renderBetween(column, columnSql, criterion, params, paramCounter);
            case IS_NULL -> columnSql + " IS NULL";
            case IS_NOT_NULL -> columnSql + " IS NOT NULL";
        };
    }

    private String renderComparison(
            Column column,
            String columnSql,
            FieldCriterion criterion,
            Map<String, Object> params,
            int[] paramCounter) {
        if (criterion.value() == null) {
            return criterion.operator() == ComparisonOperator.NE
                    ? columnSql + " IS NOT NULL"
                    : columnSql + " IS NULL";
        }
        String param = nextParam(paramCounter);
        params.put(param, valueBinder.toJdbc(column, criterion.value()));
        return columnSql + " " + criterion.operator().sql() + " :" + param;
    }

    private String renderIn(
            Column column,
            String columnSql,
            FieldCriterion criterion,
            Map<String, Collection<?>> collectionParams,
            int[] paramCounter) {
        List<Object> values = new ArrayList<>();
        for (Object value : criterion.values()) {
            if (value == null) {
                throw new MicroOrmException("IN criterion does not support null values for column: " + criterion.fieldName());
            }
            values.add(valueBinder.toJdbc(column, value));
        }
        String param = nextParam(paramCounter);
        collectionParams.put(param, values);
        String operator = criterion.kind() == CriterionKind.NOT_IN ? "NOT IN" : "IN";
        return columnSql + " " + operator + " (:" + param + ")";
    }

    private String renderBetween(
            Column column,
            String columnSql,
            FieldCriterion criterion,
            Map<String, Object> params,
            int[] paramCounter) {
        if (criterion.values().size() != 2) {
            throw new MicroOrmException("BETWEEN criterion requires exactly two bounds for column: " + criterion.fieldName());
        }
        String lowerParam = nextParam(paramCounter);
        String upperParam = nextParam(paramCounter);
        params.put(lowerParam, valueBinder.toJdbc(column, criterion.values().get(0)));
        params.put(upperParam, valueBinder.toJdbc(column, criterion.values().get(1)));
        String operator = criterion.kind() == CriterionKind.NOT_BETWEEN ? "NOT BETWEEN" : "BETWEEN";
        return columnSql + " " + operator + " :" + lowerParam + " AND :" + upperParam;
    }

    private String renderColumns(DynamicTable table, List<String> columnNames) {
        List<String> columns = new ArrayList<>();
        for (String columnName : columnNames) {
            columns.add(columnSql(table, resolveRootColumn(table, columnName, "group")));
        }
        return String.join(", ", columns);
    }

    private void mergeSubqueryParameters(
            Query query,
            Map<String, Object> params,
            Map<String, Collection<?>> collectionParams) {
        for (String name : query.parameters().keySet()) {
            if (params.containsKey(name) || collectionParams.containsKey(name)) {
                throw new MicroOrmException("Duplicate query parameter: " + name);
            }
        }
        for (String name : query.collectionParameters().keySet()) {
            if (params.containsKey(name) || collectionParams.containsKey(name)) {
                throw new MicroOrmException("Duplicate query parameter: " + name);
            }
        }
        params.putAll(query.parameters());
        collectionParams.putAll(query.collectionParameters());
    }

    private Column resolveColumn(DynamicTable table, String name) {
        return resolveRootColumn(table, name, "criterion");
    }

    private Column resolveRootColumn(DynamicTable table, String name, String operation) {
        if (name != null && name.contains(".")) {
            throw new MicroOrmException("Dynamic " + operation + " supports only root columns, got: " + name);
        }
        return table.columnByName(name);
    }

    private String columnSql(DynamicTable table, Column column) {
        return dialect.sqlName(table.tableIdentifier()) + "." + dialect.sqlName(column.columnIdentifier());
    }

    private static void requireMatchingTable(DynamicTable table, String queryTableName, String operation) {
        if (!table.name().equals(queryTableName)) {
            throw new MicroOrmException(operation + " table '" + queryTableName
                    + "' does not match dynamic table '" + table.name() + "'");
        }
    }

    private static void requireWhereOrAllRows(Criterion criterion, boolean allRows, String operation) {
        if (criterion == null && !allRows) {
            throw new MicroOrmException(operation + " requires where(...) or explicit allRows()");
        }
    }

    private static String nextParam(int[] paramCounter) {
        return "p" + paramCounter[0]++;
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
