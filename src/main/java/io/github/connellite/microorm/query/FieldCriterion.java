package io.github.connellite.microorm.query;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Predicate that compares one SQL expression with a value, collection, expression, or SQL {@code NULL}.
 *
 * @param expression left-hand SQL expression
 * @param kind predicate shape rendered to SQL
 * @param operator comparison operator for {@link CriterionKind#COMPARISON}; {@code null} otherwise
 * @param value scalar, pattern, or right-hand {@link ExprPath} / {@link QueryExpression}; {@code null} for
 *              {@code IS NULL}, {@code IS NOT NULL}, {@code IN}, and ranges
 * @param values collection values for {@link CriterionKind#IN}, {@link CriterionKind#NOT_IN}, or range bounds; empty otherwise
 * @param ignoreCase whether to wrap compared SQL expressions in {@code LOWER(...)}
 */
public record FieldCriterion(
        QueryExpression expression,
        CriterionKind kind,
        ComparisonOperator operator,
        Object value,
        List<?> values,
        boolean ignoreCase) implements Criterion {

    public FieldCriterion {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(kind, "kind");
        values = values == null ? List.of() : List.copyOf(values);
    }

    /** Left-hand field or column name when {@link #expression()} is a mapped field; otherwise a label. */
    public String fieldName() {
        return expression.describe();
    }

    static FieldCriterion comparison(
            QueryExpression expression,
            ComparisonOperator operator,
            Object value,
            boolean ignoreCase) {
        Objects.requireNonNull(operator, "operator");
        return new FieldCriterion(expression, CriterionKind.COMPARISON, operator, value, List.of(), ignoreCase);
    }

    static FieldCriterion in(QueryExpression expression, Collection<?> values, boolean ignoreCase) {
        return collection(expression, CriterionKind.IN, values, ignoreCase);
    }

    static FieldCriterion notIn(QueryExpression expression, Collection<?> values, boolean ignoreCase) {
        return collection(expression, CriterionKind.NOT_IN, values, ignoreCase);
    }

    private static FieldCriterion collection(
            QueryExpression expression,
            CriterionKind kind,
            Collection<?> values,
            boolean ignoreCase) {
        Objects.requireNonNull(values, "values");
        return new FieldCriterion(expression, kind, null, null, List.copyOf(values), ignoreCase);
    }

    static FieldCriterion like(QueryExpression expression, String pattern, boolean ignoreCase) {
        Objects.requireNonNull(pattern, "pattern");
        return new FieldCriterion(expression, CriterionKind.LIKE, null, pattern, List.of(), ignoreCase);
    }

    static FieldCriterion notLike(QueryExpression expression, String pattern, boolean ignoreCase) {
        Objects.requireNonNull(pattern, "pattern");
        return new FieldCriterion(expression, CriterionKind.NOT_LIKE, null, pattern, List.of(), ignoreCase);
    }

    static FieldCriterion between(QueryExpression expression, Object lower, Object upper, boolean ignoreCase) {
        return between(expression, CriterionKind.BETWEEN, lower, upper, ignoreCase);
    }

    static FieldCriterion notBetween(QueryExpression expression, Object lower, Object upper, boolean ignoreCase) {
        return between(expression, CriterionKind.NOT_BETWEEN, lower, upper, ignoreCase);
    }

    private static FieldCriterion between(
            QueryExpression expression,
            CriterionKind kind,
            Object lower,
            Object upper,
            boolean ignoreCase) {
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(upper, "upper");
        return new FieldCriterion(expression, kind, null, null, List.of(lower, upper), ignoreCase);
    }

    static FieldCriterion isNull(QueryExpression expression) {
        return new FieldCriterion(expression, CriterionKind.IS_NULL, null, null, List.of(), false);
    }

    static FieldCriterion isNotNull(QueryExpression expression) {
        return new FieldCriterion(expression, CriterionKind.IS_NOT_NULL, null, null, List.of(), false);
    }
}
