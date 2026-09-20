package io.github.connellite.microorm.query;

import io.github.connellite.microorm.sql.Query;

import java.util.Collection;
import java.util.Objects;

/**
 * Reference to a mapped entity field used to build criteria and sort orders.
 * <p>
 * The name may be either the Java field name or the physical column name. Joined fields use
 * {@code relation.field} and require a matching {@link EntitySelect#join(String)} declaration.
 * The path is resolved against the registered entity model when the query is executed.
 * {@link #lower()} wraps compared SQL expressions in {@code LOWER(...)} for case-insensitive matching.
 *
 * @param name Java field name, mapped column name, or joined path
 * @param ignoreCase whether predicates and sort orders wrap expressions in {@code LOWER(...)}
 */
public record FieldPath(String name, boolean ignoreCase) {

    public FieldPath {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name cannot be blank");
        }
    }

    /** Creates a case-sensitive field path. */
    public FieldPath(String name) {
        this(name, false);
    }

    /**
     * Wraps this field in SQL {@code LOWER(...)} for subsequent predicates and sort orders.
     * Bound values are also wrapped so {@code lower().eq("Ada")} matches {@code ada}.
     */
    public FieldPath lower() {
        return ignoreCase ? this : new FieldPath(name, true);
    }

    /** Builds {@code field = value}. A {@code null} value is rendered as {@code IS NULL}. */
    public Criterion eq(Object value) {
        if (value == null) {
            return isNull();
        }
        return FieldCriterion.comparison(name, ComparisonOperator.EQ, value, ignoreCase);
    }

    /** Builds {@code LOWER(field) = LOWER(value)}. */
    public Criterion equalsIgnoreCase(String value) {
        return lower().eq(value);
    }

    /** Builds {@code field <> value}. A {@code null} value is rendered as {@code IS NOT NULL}. */
    public Criterion ne(Object value) {
        if (value == null) {
            return isNotNull();
        }
        return FieldCriterion.comparison(name, ComparisonOperator.NE, value, ignoreCase);
    }

    /** Builds {@code LOWER(field) <> LOWER(value)}. */
    public Criterion notEqualsIgnoreCase(String value) {
        return lower().ne(value);
    }

    /** Builds {@code field < value}. */
    public Criterion lt(Object value) {
        return FieldCriterion.comparison(name, ComparisonOperator.LT, value, ignoreCase);
    }

    /** Builds {@code field <= value}. */
    public Criterion le(Object value) {
        return FieldCriterion.comparison(name, ComparisonOperator.LE, value, ignoreCase);
    }

    /** Builds {@code field > value}. */
    public Criterion gt(Object value) {
        return FieldCriterion.comparison(name, ComparisonOperator.GT, value, ignoreCase);
    }

    /** Builds {@code field >= value}. */
    public Criterion ge(Object value) {
        return FieldCriterion.comparison(name, ComparisonOperator.GE, value, ignoreCase);
    }

    /** Builds {@code field IN (...)}. Empty collections are rejected. */
    public Criterion in(Collection<?> values) {
        return FieldCriterion.in(name, values, ignoreCase);
    }

    /** Builds {@code field IN (subquery)}. */
    public Criterion in(Query query) {
        return InSubqueryCriterion.in(name, query, ignoreCase);
    }

    /** Builds {@code field IN (entity subquery)}. */
    public Criterion in(EntitySelect<?> query) {
        return InSubqueryCriterion.in(name, query, ignoreCase);
    }

    /** Builds {@code field NOT IN (...)}. Empty collections are rejected. */
    public Criterion notIn(Collection<?> values) {
        return FieldCriterion.notIn(name, values, ignoreCase);
    }

    /** Builds {@code field NOT IN (subquery)}. */
    public Criterion notIn(Query query) {
        return InSubqueryCriterion.notIn(name, query, ignoreCase);
    }

    /** Builds {@code field NOT IN (entity subquery)}. */
    public Criterion notIn(EntitySelect<?> query) {
        return InSubqueryCriterion.notIn(name, query, ignoreCase);
    }

    /** Builds {@code field = ANY (subquery)}. */
    public Criterion eqAny(Query query) {
        return any(ComparisonOperator.EQ, query);
    }

    /** Builds {@code field = ANY (entity subquery)}. */
    public Criterion eqAny(EntitySelect<?> query) {
        return any(ComparisonOperator.EQ, query);
    }

    /** Builds {@code field <> ANY (subquery)}. */
    public Criterion neAny(Query query) {
        return any(ComparisonOperator.NE, query);
    }

    /** Builds {@code field <> ANY (entity subquery)}. */
    public Criterion neAny(EntitySelect<?> query) {
        return any(ComparisonOperator.NE, query);
    }

    /** Builds {@code field < ANY (subquery)}. */
    public Criterion ltAny(Query query) {
        return any(ComparisonOperator.LT, query);
    }

    /** Builds {@code field < ANY (entity subquery)}. */
    public Criterion ltAny(EntitySelect<?> query) {
        return any(ComparisonOperator.LT, query);
    }

    /** Builds {@code field <= ANY (subquery)}. */
    public Criterion leAny(Query query) {
        return any(ComparisonOperator.LE, query);
    }

    /** Builds {@code field <= ANY (entity subquery)}. */
    public Criterion leAny(EntitySelect<?> query) {
        return any(ComparisonOperator.LE, query);
    }

    /** Builds {@code field > ANY (subquery)}. */
    public Criterion gtAny(Query query) {
        return any(ComparisonOperator.GT, query);
    }

    /** Builds {@code field > ANY (entity subquery)}. */
    public Criterion gtAny(EntitySelect<?> query) {
        return any(ComparisonOperator.GT, query);
    }

    /** Builds {@code field >= ANY (subquery)}. */
    public Criterion geAny(Query query) {
        return any(ComparisonOperator.GE, query);
    }

    /** Builds {@code field >= ANY (entity subquery)}. */
    public Criterion geAny(EntitySelect<?> query) {
        return any(ComparisonOperator.GE, query);
    }

    /** Builds {@code field <operator> ANY (subquery)}. */
    public Criterion any(ComparisonOperator operator, Query query) {
        return quantified(operator, SubqueryQuantifier.ANY, query);
    }

    /** Builds {@code field <operator> ANY (entity subquery)}. */
    public Criterion any(ComparisonOperator operator, EntitySelect<?> query) {
        return quantified(operator, SubqueryQuantifier.ANY, query);
    }

    /** Builds {@code field = ALL (subquery)}. */
    public Criterion eqAll(Query query) {
        return all(ComparisonOperator.EQ, query);
    }

    /** Builds {@code field = ALL (entity subquery)}. */
    public Criterion eqAll(EntitySelect<?> query) {
        return all(ComparisonOperator.EQ, query);
    }

    /** Builds {@code field <> ALL (subquery)}. */
    public Criterion neAll(Query query) {
        return all(ComparisonOperator.NE, query);
    }

    /** Builds {@code field <> ALL (entity subquery)}. */
    public Criterion neAll(EntitySelect<?> query) {
        return all(ComparisonOperator.NE, query);
    }

    /** Builds {@code field < ALL (subquery)}. */
    public Criterion ltAll(Query query) {
        return all(ComparisonOperator.LT, query);
    }

    /** Builds {@code field < ALL (entity subquery)}. */
    public Criterion ltAll(EntitySelect<?> query) {
        return all(ComparisonOperator.LT, query);
    }

    /** Builds {@code field <= ALL (subquery)}. */
    public Criterion leAll(Query query) {
        return all(ComparisonOperator.LE, query);
    }

    /** Builds {@code field <= ALL (entity subquery)}. */
    public Criterion leAll(EntitySelect<?> query) {
        return all(ComparisonOperator.LE, query);
    }

    /** Builds {@code field > ALL (subquery)}. */
    public Criterion gtAll(Query query) {
        return all(ComparisonOperator.GT, query);
    }

    /** Builds {@code field > ALL (entity subquery)}. */
    public Criterion gtAll(EntitySelect<?> query) {
        return all(ComparisonOperator.GT, query);
    }

    /** Builds {@code field >= ALL (subquery)}. */
    public Criterion geAll(Query query) {
        return all(ComparisonOperator.GE, query);
    }

    /** Builds {@code field >= ALL (entity subquery)}. */
    public Criterion geAll(EntitySelect<?> query) {
        return all(ComparisonOperator.GE, query);
    }

    /** Builds {@code field <operator> ALL (subquery)}. */
    public Criterion all(ComparisonOperator operator, Query query) {
        return quantified(operator, SubqueryQuantifier.ALL, query);
    }

    /** Builds {@code field <operator> ALL (entity subquery)}. */
    public Criterion all(ComparisonOperator operator, EntitySelect<?> query) {
        return quantified(operator, SubqueryQuantifier.ALL, query);
    }

    /** Builds {@code field LIKE pattern}. */
    public Criterion like(String pattern) {
        return FieldCriterion.like(name, pattern, ignoreCase);
    }

    /** Builds {@code LOWER(field) LIKE LOWER(pattern)}. */
    public Criterion likeIgnoreCase(String pattern) {
        return lower().like(pattern);
    }

    /** Builds {@code field NOT LIKE pattern}. */
    public Criterion notLike(String pattern) {
        return FieldCriterion.notLike(name, pattern, ignoreCase);
    }

    /** Builds {@code LOWER(field) NOT LIKE LOWER(pattern)}. */
    public Criterion notLikeIgnoreCase(String pattern) {
        return lower().notLike(pattern);
    }

    /** Builds {@code field BETWEEN lower AND upper}. */
    public Criterion between(Object lower, Object upper) {
        return FieldCriterion.between(name, lower, upper, ignoreCase);
    }

    /** Builds {@code field NOT BETWEEN lower AND upper}. */
    public Criterion notBetween(Object lower, Object upper) {
        return FieldCriterion.notBetween(name, lower, upper, ignoreCase);
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
        return new Order(name, OrderDirection.ASC, ignoreCase);
    }

    /** Builds descending {@code ORDER BY field DESC}. */
    public Order desc() {
        return new Order(name, OrderDirection.DESC, ignoreCase);
    }

    private Criterion quantified(ComparisonOperator operator, SubqueryQuantifier quantifier, Query query) {
        Objects.requireNonNull(operator, "operator");
        return new QuantifiedSubqueryCriterion(name, operator, quantifier, query, null, ignoreCase);
    }

    private Criterion quantified(ComparisonOperator operator, SubqueryQuantifier quantifier, EntitySelect<?> query) {
        Objects.requireNonNull(operator, "operator");
        return new QuantifiedSubqueryCriterion(name, operator, quantifier, null, query, ignoreCase);
    }
}
