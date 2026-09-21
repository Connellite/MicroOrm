package io.github.connellite.microorm.query;

import io.github.connellite.microorm.sql.Query;

import java.util.Objects;

/**
 * Predicate comparing an SQL expression to {@code ANY (...)} or {@code ALL (...)} subquery result.
 *
 * @param expression left-hand SQL expression
 * @param operator comparison operator before the quantifier
 * @param quantifier SQL quantifier
 * @param query raw subquery producing comparable values
 * @param entitySelect entity subquery producing comparable values
 * @param ignoreCase whether to wrap the compared expression in {@code LOWER(...)}
 */
public record QuantifiedSubqueryCriterion(
        QueryExpression expression,
        ComparisonOperator operator,
        SubqueryQuantifier quantifier,
        Query query,
        EntitySelect<?> entitySelect,
        boolean ignoreCase) implements Criterion {

    public QuantifiedSubqueryCriterion {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(quantifier, "quantifier");
        if ((query == null) == (entitySelect == null)) {
            throw new IllegalArgumentException("Exactly one subquery must be provided");
        }
    }

    /** Left-hand field name when the expression is a mapped field; otherwise a label. */
    public String fieldName() {
        return expression.describe();
    }
}
