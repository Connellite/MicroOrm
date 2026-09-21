package io.github.connellite.microorm.query;

import io.github.connellite.microorm.sql.Query;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Comparable SQL expression used to build criteria and sort orders: a {@link FieldPath} or {@link FunctionPath}.
 */
public interface ExprPath {

    /** Underlying SQL expression rendered when the query is executed. */
    QueryExpression expression();

    /** Whether predicates and sort orders wrap this expression in {@code LOWER(...)}. */
    boolean ignoreCase();

    /**
     * Wraps this expression in SQL {@code LOWER(...)} for subsequent predicates and sort orders.
     * Bound comparison values are also wrapped.
     */
    ExprPath lower();

    /** Builds {@code expr = value}. A {@code null} value is rendered as {@code IS NULL}. */
    default Criterion eq(Object value) {
        if (value == null) {
            return isNull();
        }
        return FieldCriterion.comparison(expression(), ComparisonOperator.EQ, value, ignoreCase());
    }

    /** Builds {@code LOWER(expr) = LOWER(value)}. */
    default Criterion equalsIgnoreCase(String value) {
        return lower().eq(value);
    }

    /** Builds {@code expr <> value}. A {@code null} value is rendered as {@code IS NOT NULL}. */
    default Criterion ne(Object value) {
        if (value == null) {
            return isNotNull();
        }
        return FieldCriterion.comparison(expression(), ComparisonOperator.NE, value, ignoreCase());
    }

    /** Builds {@code LOWER(expr) <> LOWER(value)}. */
    default Criterion notEqualsIgnoreCase(String value) {
        return lower().ne(value);
    }

    /** Builds {@code expr < value}. */
    default Criterion lt(Object value) {
        return FieldCriterion.comparison(expression(), ComparisonOperator.LT, value, ignoreCase());
    }

    /** Builds {@code expr <= value}. */
    default Criterion le(Object value) {
        return FieldCriterion.comparison(expression(), ComparisonOperator.LE, value, ignoreCase());
    }

    /** Builds {@code expr > value}. */
    default Criterion gt(Object value) {
        return FieldCriterion.comparison(expression(), ComparisonOperator.GT, value, ignoreCase());
    }

    /** Builds {@code expr >= value}. */
    default Criterion ge(Object value) {
        return FieldCriterion.comparison(expression(), ComparisonOperator.GE, value, ignoreCase());
    }

    /** Builds {@code expr IN (...)}. An empty collection is rendered as {@code 1 = 0}. */
    default Criterion in(Collection<?> values) {
        return FieldCriterion.in(expression(), values, ignoreCase());
    }

    /** Builds {@code expr IN (...)} from individual values. */
    default Criterion in(Object... values) {
        Objects.requireNonNull(values, "values");
        return in(List.of(values));
    }

    /** Builds {@code expr IN (subquery)}. */
    default Criterion in(Query query) {
        return InSubqueryCriterion.in(expression(), query, ignoreCase());
    }

    /** Builds {@code expr IN (entity subquery)}. */
    default Criterion in(EntitySelect<?> query) {
        return InSubqueryCriterion.in(expression(), query, ignoreCase());
    }

    /** Builds {@code expr NOT IN (...)}. An empty collection is rendered as {@code 1 = 1}. */
    default Criterion notIn(Collection<?> values) {
        return FieldCriterion.notIn(expression(), values, ignoreCase());
    }

    /** Builds {@code expr NOT IN (...)} from individual values. */
    default Criterion notIn(Object... values) {
        Objects.requireNonNull(values, "values");
        return notIn(List.of(values));
    }

    /** Builds {@code expr NOT IN (subquery)}. */
    default Criterion notIn(Query query) {
        return InSubqueryCriterion.notIn(expression(), query, ignoreCase());
    }

    /** Builds {@code expr NOT IN (entity subquery)}. */
    default Criterion notIn(EntitySelect<?> query) {
        return InSubqueryCriterion.notIn(expression(), query, ignoreCase());
    }

    /** Builds {@code expr = ANY (subquery)}. */
    default Criterion eqAny(Query query) {
        return any(ComparisonOperator.EQ, query);
    }

    /** Builds {@code expr = ANY (entity subquery)}. */
    default Criterion eqAny(EntitySelect<?> query) {
        return any(ComparisonOperator.EQ, query);
    }

    /** Builds {@code expr <> ANY (subquery)}. */
    default Criterion neAny(Query query) {
        return any(ComparisonOperator.NE, query);
    }

    /** Builds {@code expr <> ANY (entity subquery)}. */
    default Criterion neAny(EntitySelect<?> query) {
        return any(ComparisonOperator.NE, query);
    }

    /** Builds {@code expr < ANY (subquery)}. */
    default Criterion ltAny(Query query) {
        return any(ComparisonOperator.LT, query);
    }

    /** Builds {@code expr < ANY (entity subquery)}. */
    default Criterion ltAny(EntitySelect<?> query) {
        return any(ComparisonOperator.LT, query);
    }

    /** Builds {@code expr <= ANY (subquery)}. */
    default Criterion leAny(Query query) {
        return any(ComparisonOperator.LE, query);
    }

    /** Builds {@code expr <= ANY (entity subquery)}. */
    default Criterion leAny(EntitySelect<?> query) {
        return any(ComparisonOperator.LE, query);
    }

    /** Builds {@code expr > ANY (subquery)}. */
    default Criterion gtAny(Query query) {
        return any(ComparisonOperator.GT, query);
    }

    /** Builds {@code expr > ANY (entity subquery)}. */
    default Criterion gtAny(EntitySelect<?> query) {
        return any(ComparisonOperator.GT, query);
    }

    /** Builds {@code expr >= ANY (subquery)}. */
    default Criterion geAny(Query query) {
        return any(ComparisonOperator.GE, query);
    }

    /** Builds {@code expr >= ANY (entity subquery)}. */
    default Criterion geAny(EntitySelect<?> query) {
        return any(ComparisonOperator.GE, query);
    }

    /** Builds {@code expr <operator> ANY (subquery)}. */
    default Criterion any(ComparisonOperator operator, Query query) {
        return quantified(operator, SubqueryQuantifier.ANY, query);
    }

    /** Builds {@code expr <operator> ANY (entity subquery)}. */
    default Criterion any(ComparisonOperator operator, EntitySelect<?> query) {
        return quantified(operator, SubqueryQuantifier.ANY, query);
    }

    /** Builds {@code expr = ALL (subquery)}. */
    default Criterion eqAll(Query query) {
        return all(ComparisonOperator.EQ, query);
    }

    /** Builds {@code expr = ALL (entity subquery)}. */
    default Criterion eqAll(EntitySelect<?> query) {
        return all(ComparisonOperator.EQ, query);
    }

    /** Builds {@code expr <> ALL (subquery)}. */
    default Criterion neAll(Query query) {
        return all(ComparisonOperator.NE, query);
    }

    /** Builds {@code expr <> ALL (entity subquery)}. */
    default Criterion neAll(EntitySelect<?> query) {
        return all(ComparisonOperator.NE, query);
    }

    /** Builds {@code expr < ALL (subquery)}. */
    default Criterion ltAll(Query query) {
        return all(ComparisonOperator.LT, query);
    }

    /** Builds {@code expr < ALL (entity subquery)}. */
    default Criterion ltAll(EntitySelect<?> query) {
        return all(ComparisonOperator.LT, query);
    }

    /** Builds {@code expr <= ALL (subquery)}. */
    default Criterion leAll(Query query) {
        return all(ComparisonOperator.LE, query);
    }

    /** Builds {@code expr <= ALL (entity subquery)}. */
    default Criterion leAll(EntitySelect<?> query) {
        return all(ComparisonOperator.LE, query);
    }

    /** Builds {@code expr > ALL (subquery)}. */
    default Criterion gtAll(Query query) {
        return all(ComparisonOperator.GT, query);
    }

    /** Builds {@code expr > ALL (entity subquery)}. */
    default Criterion gtAll(EntitySelect<?> query) {
        return all(ComparisonOperator.GT, query);
    }

    /** Builds {@code expr >= ALL (subquery)}. */
    default Criterion geAll(Query query) {
        return all(ComparisonOperator.GE, query);
    }

    /** Builds {@code expr >= ALL (entity subquery)}. */
    default Criterion geAll(EntitySelect<?> query) {
        return all(ComparisonOperator.GE, query);
    }

    /** Builds {@code expr <operator> ALL (subquery)}. */
    default Criterion all(ComparisonOperator operator, Query query) {
        return quantified(operator, SubqueryQuantifier.ALL, query);
    }

    /** Builds {@code expr <operator> ALL (entity subquery)}. */
    default Criterion all(ComparisonOperator operator, EntitySelect<?> query) {
        return quantified(operator, SubqueryQuantifier.ALL, query);
    }

    /** Builds {@code expr LIKE pattern}. */
    default Criterion like(String pattern) {
        return FieldCriterion.like(expression(), pattern, ignoreCase());
    }

    /** Builds {@code LOWER(expr) LIKE LOWER(pattern)}. */
    default Criterion likeIgnoreCase(String pattern) {
        return lower().like(pattern);
    }

    /** Builds {@code expr NOT LIKE pattern}. */
    default Criterion notLike(String pattern) {
        return FieldCriterion.notLike(expression(), pattern, ignoreCase());
    }

    /** Builds {@code LOWER(expr) NOT LIKE LOWER(pattern)}. */
    default Criterion notLikeIgnoreCase(String pattern) {
        return lower().notLike(pattern);
    }

    /** Builds {@code expr BETWEEN lower AND upper}. */
    default Criterion between(Object lower, Object upper) {
        return FieldCriterion.between(expression(), lower, upper, ignoreCase());
    }

    /** Builds {@code expr NOT BETWEEN lower AND upper}. */
    default Criterion notBetween(Object lower, Object upper) {
        return FieldCriterion.notBetween(expression(), lower, upper, ignoreCase());
    }

    /** Builds {@code expr IS NULL}. */
    default Criterion isNull() {
        return FieldCriterion.isNull(expression());
    }

    /** Builds {@code expr IS NOT NULL}. */
    default Criterion isNotNull() {
        return FieldCriterion.isNotNull(expression());
    }

    /** Builds ascending {@code ORDER BY expr ASC}. */
    default Order asc() {
        return new Order(expression(), OrderDirection.ASC, ignoreCase());
    }

    /** Builds descending {@code ORDER BY expr DESC}. */
    default Order desc() {
        return new Order(expression(), OrderDirection.DESC, ignoreCase());
    }

    private Criterion quantified(ComparisonOperator operator, SubqueryQuantifier quantifier, Query query) {
        Objects.requireNonNull(operator, "operator");
        return new QuantifiedSubqueryCriterion(expression(), operator, quantifier, query, null, ignoreCase());
    }

    private Criterion quantified(ComparisonOperator operator, SubqueryQuantifier quantifier, EntitySelect<?> query) {
        Objects.requireNonNull(operator, "operator");
        return new QuantifiedSubqueryCriterion(expression(), operator, quantifier, null, query, ignoreCase());
    }
}
