package io.github.connellite.microorm.query;

import java.util.Collection;

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

    /** Builds {@code field LIKE pattern}. */
    public Criterion like(String pattern) {
        return FieldCriterion.like(name, pattern);
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
}
