package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.query.ComparisonOperator;
import io.github.connellite.microorm.query.CompositeCriterion;
import io.github.connellite.microorm.query.Criterion;
import io.github.connellite.microorm.query.CriterionKind;
import io.github.connellite.microorm.query.ExistsCriterion;
import io.github.connellite.microorm.query.FieldCriterion;
import io.github.connellite.microorm.query.InSubqueryCriterion;
import io.github.connellite.microorm.query.NotCriterion;
import io.github.connellite.microorm.query.Order;
import io.github.connellite.microorm.query.QuantifiedSubqueryCriterion;
import io.github.connellite.microorm.query.QueryExpression;
import io.github.connellite.microorm.query.QueryExpressions;
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
                RenderedExpr expr = renderExpression(table, order.expression(), params, paramCounter);
                orderSql.add(applyIgnoreCase(expr.sql(), order.ignoreCase()) + " " + order.direction().name());
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
            RenderedExpr left = renderExpression(table, quantified.expression(), params, paramCounter);
            mergeSubqueryParameters(quantified.query(), params, collectionParams);
            return applyIgnoreCase(left.sql(), quantified.ignoreCase()) + " " + quantified.operator().sql()
                    + " " + quantified.quantifier().name() + " (" + quantified.query().sql() + ")";
        }
        if (criterion instanceof InSubqueryCriterion inSubquery) {
            if (inSubquery.entitySelect() != null) {
                throw new MicroOrmException("Dynamic criteria do not support EntitySelect subqueries");
            }
            RenderedExpr left = renderExpression(table, inSubquery.expression(), params, paramCounter);
            mergeSubqueryParameters(inSubquery.query(), params, collectionParams);
            return applyIgnoreCase(left.sql(), inSubquery.ignoreCase())
                    + (inSubquery.negated() ? " NOT IN (" : " IN (")
                    + inSubquery.query().sql() + ")";
        }
        throw new MicroOrmException("Unsupported criterion type: " + criterion.getClass().getName());
    }

    private String renderFieldCriterion(
            DynamicTable table,
            FieldCriterion criterion,
            Map<String, Object> params,
            Map<String, Collection<?>> collectionParams,
            int[] paramCounter) {
        RenderedExpr left = renderExpression(table, criterion.expression(), params, paramCounter);
        String columnSql = applyIgnoreCase(left.sql(), criterion.ignoreCase());
        return switch (criterion.kind()) {
            case COMPARISON -> renderComparison(table, left, columnSql, criterion, params, paramCounter);
            case IN, NOT_IN -> renderIn(table, left, columnSql, criterion, params, collectionParams, paramCounter);
            case LIKE -> {
                String param = nextParam(paramCounter);
                params.put(param, jdbcValue(left.column(), criterion.value()));
                yield columnSql + " LIKE " + applyIgnoreCase(":" + param, criterion.ignoreCase());
            }
            case NOT_LIKE -> {
                String param = nextParam(paramCounter);
                params.put(param, jdbcValue(left.column(), criterion.value()));
                yield columnSql + " NOT LIKE " + applyIgnoreCase(":" + param, criterion.ignoreCase());
            }
            case BETWEEN, NOT_BETWEEN -> renderBetween(table, left, columnSql, criterion, params, paramCounter);
            case IS_NULL -> left.sql() + " IS NULL";
            case IS_NOT_NULL -> left.sql() + " IS NOT NULL";
        };
    }

    private String renderComparison(
            DynamicTable table,
            RenderedExpr left,
            String columnSql,
            FieldCriterion criterion,
            Map<String, Object> params,
            int[] paramCounter) {
        if (criterion.value() == null) {
            return criterion.operator() == ComparisonOperator.NE
                    ? columnSql + " IS NOT NULL"
                    : columnSql + " IS NULL";
        }
        QueryExpression rhs = QueryExpressions.asExpression(criterion.value());
        if (rhs != null) {
            String rightSql = applyIgnoreCase(
                    renderExpression(table, rhs, params, paramCounter).sql(),
                    criterion.ignoreCase());
            return columnSql + " " + criterion.operator().sql() + " " + rightSql;
        }
        String param = nextParam(paramCounter);
        params.put(param, jdbcValue(left.column(), criterion.value()));
        return columnSql + " " + criterion.operator().sql() + " " + applyIgnoreCase(":" + param, criterion.ignoreCase());
    }

    private String renderIn(
            DynamicTable table,
            RenderedExpr left,
            String columnSql,
            FieldCriterion criterion,
            Map<String, Object> params,
            Map<String, Collection<?>> collectionParams,
            int[] paramCounter) {
        if (criterion.values().isEmpty()) {
            return criterion.kind() == CriterionKind.NOT_IN ? "1 = 1" : "1 = 0";
        }
        String operator = criterion.kind() == CriterionKind.NOT_IN ? "NOT IN" : "IN";
        boolean slotwise = criterion.ignoreCase();
        for (Object value : criterion.values()) {
            if (QueryExpressions.isExpression(value)) {
                slotwise = true;
                break;
            }
        }
        if (slotwise) {
            List<String> slots = new ArrayList<>();
            for (Object value : criterion.values()) {
                if (value == null) {
                    throw QueryExpressions.unsupportedNullIn("column: " + criterion.fieldName());
                }
                QueryExpression expr = QueryExpressions.asExpression(value);
                if (expr != null) {
                    slots.add(applyIgnoreCase(
                            renderExpression(table, expr, params, paramCounter).sql(),
                            criterion.ignoreCase()));
                } else {
                    String param = nextParam(paramCounter);
                    params.put(param, jdbcValue(left.column(), value));
                    slots.add(applyIgnoreCase(":" + param, criterion.ignoreCase()));
                }
            }
            return columnSql + " " + operator + " (" + String.join(", ", slots) + ")";
        }
        List<Object> values = new ArrayList<>();
        for (Object value : criterion.values()) {
            if (value == null) {
                throw QueryExpressions.unsupportedNullIn("column: " + criterion.fieldName());
            }
            values.add(jdbcValue(left.column(), value));
        }
        String param = nextParam(paramCounter);
        collectionParams.put(param, values);
        return columnSql + " " + operator + " (:" + param + ")";
    }

    private String renderBetween(
            DynamicTable table,
            RenderedExpr left,
            String columnSql,
            FieldCriterion criterion,
            Map<String, Object> params,
            int[] paramCounter) {
        if (criterion.values().size() != 2) {
            throw new MicroOrmException("BETWEEN criterion requires exactly two bounds for column: " + criterion.fieldName());
        }
        String operator = criterion.kind() == CriterionKind.NOT_BETWEEN ? "NOT BETWEEN" : "BETWEEN";
        return columnSql + " " + operator + " "
                + renderBound(table, left.column(), criterion.values().get(0), criterion.ignoreCase(), params, paramCounter)
                + " AND "
                + renderBound(table, left.column(), criterion.values().get(1), criterion.ignoreCase(), params, paramCounter);
    }

    private String renderBound(
            DynamicTable table,
            Column column,
            Object value,
            boolean ignoreCase,
            Map<String, Object> params,
            int[] paramCounter) {
        QueryExpression expr = QueryExpressions.asExpression(value);
        if (expr != null) {
            return applyIgnoreCase(renderExpression(table, expr, params, paramCounter).sql(), ignoreCase);
        }
        String param = nextParam(paramCounter);
        params.put(param, jdbcValue(column, value));
        return applyIgnoreCase(":" + param, ignoreCase);
    }

    private RenderedExpr renderExpression(
            DynamicTable table,
            QueryExpression expression,
            Map<String, Object> params,
            int[] paramCounter) {
        if (expression instanceof QueryExpression.Field field) {
            Column column = resolveColumn(table, field.name());
            return new RenderedExpr(columnSql(table, column), column);
        }
        if (expression instanceof QueryExpression.Function function) {
            List<String> args = new ArrayList<>();
            for (QueryExpression argument : function.arguments()) {
                args.add(renderExpression(table, argument, params, paramCounter).sql());
            }
            String name = QueryExpressions.qualifiedFunctionName(function.name(), dialect::sqlName);
            return new RenderedExpr(name + "(" + String.join(", ", args) + ")", null);
        }
        if (expression instanceof QueryExpression.Bound bound) {
            String param = nextParam(paramCounter);
            params.put(param, bound.value());
            return new RenderedExpr(":" + param, null);
        }
        return new RenderedExpr("NULL", null);
    }

    private Object jdbcValue(Column column, Object value) {
        if (column == null) {
            return value;
        }
        return valueBinder.toJdbc(column, value);
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

    private static String applyIgnoreCase(String sql, boolean ignoreCase) {
        return ignoreCase ? "LOWER(" + sql + ")" : sql;
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

    private record RenderedExpr(String sql, Column column) {
    }
}
