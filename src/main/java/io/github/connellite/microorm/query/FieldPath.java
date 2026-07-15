package io.github.connellite.microorm.query;

import io.github.connellite.microorm.sql.Query;

import java.util.Collection;
import java.util.Objects;

/**
 * Reference to a mapped entity field used to build criteria and sort orders.
 * <p>
 * The name may be either the Java field name or the physical column name. Joined fields use
 * {@code relation.field} and require a matching {@link EntityQuery#join(String)} declaration.
 * The path is resolved against the registered entity model when the query is executed.
 *
 * @param name Java field name, mapped column name, or joined path
 */
public record FieldPath(String name) {

    public FieldPath {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name cannot be blank");
        }
    }

    /** Builds {@code field = value}. A {@code null} value is rendered as {@code IS NULL}. */
    public Criterion eq(Object value) {
        if (value == null) {
            return isNull();
        }
        return FieldCriterion.comparison(name, ComparisonOperator.EQ, value);
    }

    /** Builds {@code field <> value}. A {@code null} value is rendered as {@code IS NOT NULL}. */
    public Criterion ne(Object value) {
        if (value == null) {
            return isNotNull();
        }
        return FieldCriterion.comparison(name, ComparisonOperator.NE, value);
    }

    /** Builds {@code field < value}. */
    public Criterion lt(Object value) {
        return FieldCriterion.comparison(name, ComparisonOperator.LT, value);
    }

    /** Builds {@code field <= value}. */
    public Criterion le(Object value) {
        return FieldCriterion.comparison(name, ComparisonOperator.LE, value);
    }

    /** Builds {@code field > value}. */
    public Criterion gt(Object value) {
        return FieldCriterion.comparison(name, ComparisonOperator.GT, value);
    }

    /** Builds {@code field >= value}. */
    public Criterion ge(Object value) {
        return FieldCriterion.comparison(name, ComparisonOperator.GE, value);
    }

    /** Builds {@code field IN (...)}. Empty collections are rejected. */
    public Criterion in(Collection<?> values) {
        return FieldCriterion.in(name, values);
    }

    /** Builds {@code field NOT IN (...)}. Empty collections are rejected. */
    public Criterion notIn(Collection<?> values) {
        return FieldCriterion.notIn(name, values);
    }

    /** Builds {@code field = ANY (subquery)}. */
    public Criterion eqAny(Query query) {
        return any(ComparisonOperator.EQ, query);
    }

    /** Builds {@code field = ANY (entity subquery)}. */
    public Criterion eqAny(EntityQuery<?> query) {
        return any(ComparisonOperator.EQ, query);
    }

    /** Builds {@code field <> ANY (subquery)}. */
    public Criterion neAny(Query query) {
        return any(ComparisonOperator.NE, query);
    }

    /** Builds {@code field <> ANY (entity subquery)}. */
    public Criterion neAny(EntityQuery<?> query) {
        return any(ComparisonOperator.NE, query);
    }

    /** Builds {@code field < ANY (subquery)}. */
    public Criterion ltAny(Query query) {
        return any(ComparisonOperator.LT, query);
    }

    /** Builds {@code field < ANY (entity subquery)}. */
    public Criterion ltAny(EntityQuery<?> query) {
        return any(ComparisonOperator.LT, query);
    }

    /** Builds {@code field <= ANY (subquery)}. */
    public Criterion leAny(Query query) {
        return any(ComparisonOperator.LE, query);
    }

    /** Builds {@code field <= ANY (entity subquery)}. */
    public Criterion leAny(EntityQuery<?> query) {
        return any(ComparisonOperator.LE, query);
    }

    /** Builds {@code field > ANY (subquery)}. */
    public Criterion gtAny(Query query) {
        return any(ComparisonOperator.GT, query);
    }

    /** Builds {@code field > ANY (entity subquery)}. */
    public Criterion gtAny(EntityQuery<?> query) {
        return any(ComparisonOperator.GT, query);
    }

    /** Builds {@code field >= ANY (subquery)}. */
    public Criterion geAny(Query query) {
        return any(ComparisonOperator.GE, query);
    }

    /** Builds {@code field >= ANY (entity subquery)}. */
    public Criterion geAny(EntityQuery<?> query) {
        return any(ComparisonOperator.GE, query);
    }

    /** Builds {@code field <operator> ANY (subquery)}. */
    public Criterion any(ComparisonOperator operator, Query query) {
        return quantified(operator, SubqueryQuantifier.ANY, query);
    }

    /** Builds {@code field <operator> ANY (entity subquery)}. */
    public Criterion any(ComparisonOperator operator, EntityQuery<?> query) {
        return quantified(operator, SubqueryQuantifier.ANY, query);
    }

    /** Builds {@code field = ALL (subquery)}. */
    public Criterion eqAll(Query query) {
        return all(ComparisonOperator.EQ, query);
    }

    /** Builds {@code field = ALL (entity subquery)}. */
    public Criterion eqAll(EntityQuery<?> query) {
        return all(ComparisonOperator.EQ, query);
    }

    /** Builds {@code field <> ALL (subquery)}. */
    public Criterion neAll(Query query) {
        return all(ComparisonOperator.NE, query);
    }

    /** Builds {@code field <> ALL (entity subquery)}. */
    public Criterion neAll(EntityQuery<?> query) {
        return all(ComparisonOperator.NE, query);
    }

    /** Builds {@code field < ALL (subquery)}. */
    public Criterion ltAll(Query query) {
        return all(ComparisonOperator.LT, query);
    }

    /** Builds {@code field < ALL (entity subquery)}. */
    public Criterion ltAll(EntityQuery<?> query) {
        return all(ComparisonOperator.LT, query);
    }

    /** Builds {@code field <= ALL (subquery)}. */
    public Criterion leAll(Query query) {
        return all(ComparisonOperator.LE, query);
    }

    /** Builds {@code field <= ALL (entity subquery)}. */
    public Criterion leAll(EntityQuery<?> query) {
        return all(ComparisonOperator.LE, query);
    }

    /** Builds {@code field > ALL (subquery)}. */
    public Criterion gtAll(Query query) {
        return all(ComparisonOperator.GT, query);
    }

    /** Builds {@code field > ALL (entity subquery)}. */
    public Criterion gtAll(EntityQuery<?> query) {
        return all(ComparisonOperator.GT, query);
    }

    /** Builds {@code field >= ALL (subquery)}. */
    public Criterion geAll(Query query) {
        return all(ComparisonOperator.GE, query);
    }

    /** Builds {@code field >= ALL (entity subquery)}. */
    public Criterion geAll(EntityQuery<?> query) {
        return all(ComparisonOperator.GE, query);
    }

    /** Builds {@code field <operator> ALL (subquery)}. */
    public Criterion all(ComparisonOperator operator, Query query) {
        return quantified(operator, SubqueryQuantifier.ALL, query);
    }

    /** Builds {@code field <operator> ALL (entity subquery)}. */
    public Criterion all(ComparisonOperator operator, EntityQuery<?> query) {
        return quantified(operator, SubqueryQuantifier.ALL, query);
    }

    /** Builds {@code field LIKE pattern}. */
    public Criterion like(String pattern) {
        return FieldCriterion.like(name, pattern);
    }

    /** Builds {@code field NOT LIKE pattern}. */
    public Criterion notLike(String pattern) {
        return FieldCriterion.notLike(name, pattern);
    }

    /** Builds {@code field BETWEEN lower AND upper}. */
    public Criterion between(Object lower, Object upper) {
        return FieldCriterion.between(name, lower, upper);
    }

    /** Builds {@code field NOT BETWEEN lower AND upper}. */
    public Criterion notBetween(Object lower, Object upper) {
        return FieldCriterion.notBetween(name, lower, upper);
    }

    /** Builds {@code field IS NULL}. */
    public Criterion isNull() {
        return FieldCriterion.isNull(name);
    }

    /** Builds {@code field IS NOT NULL}. */
    public Criterion isNotNull() {
        return FieldCriterion.isNotNull(name);
    }

    /** Builds ascending {@code ORDER BY field ASC}. */
    public Order asc() {
        return new Order(name, OrderDirection.ASC);
    }

    /** Builds descending {@code ORDER BY field DESC}. */
    public Order desc() {
        return new Order(name, OrderDirection.DESC);
    }

    private Criterion quantified(ComparisonOperator operator, SubqueryQuantifier quantifier, Query query) {
        Objects.requireNonNull(operator, "operator");
        return new QuantifiedSubqueryCriterion(name, operator, quantifier, query, null);
    }

    private Criterion quantified(ComparisonOperator operator, SubqueryQuantifier quantifier, EntityQuery<?> query) {
        Objects.requireNonNull(operator, "operator");
        return new QuantifiedSubqueryCriterion(name, operator, quantifier, null, query);
    }
}
