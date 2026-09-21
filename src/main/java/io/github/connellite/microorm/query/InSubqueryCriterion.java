package io.github.connellite.microorm.query;

import io.github.connellite.microorm.sql.Query;

import java.util.Objects;

/**
 * Predicate comparing an SQL expression to {@code IN (...)} or {@code NOT IN (...)} subquery result.
 *
 * @param expression left-hand SQL expression
 * @param negated whether to render {@code NOT IN}
 * @param query raw subquery producing comparable values
 * @param entitySelect entity subquery producing comparable values
 * @param ignoreCase whether to wrap the compared expression in {@code LOWER(...)}
 */
public record InSubqueryCriterion(
        QueryExpression expression,
        boolean negated,
        Query query,
        EntitySelect<?> entitySelect,
        boolean ignoreCase) implements Criterion {

    public InSubqueryCriterion {
        Objects.requireNonNull(expression, "expression");
        if ((query == null) == (entitySelect == null)) {
            throw new IllegalArgumentException("Exactly one subquery must be provided");
        }
    }

    /** Left-hand field name when the expression is a mapped field; otherwise a label. */
    public String fieldName() {
        return expression.describe();
    }

    static InSubqueryCriterion in(QueryExpression expression, Query query, boolean ignoreCase) {
        return new InSubqueryCriterion(expression, false, Objects.requireNonNull(query, "query"), null, ignoreCase);
    }

    static InSubqueryCriterion in(QueryExpression expression, EntitySelect<?> query, boolean ignoreCase) {
        return new InSubqueryCriterion(expression, false, null, Objects.requireNonNull(query, "query"), ignoreCase);
    }

    static InSubqueryCriterion notIn(QueryExpression expression, Query query, boolean ignoreCase) {
        return new InSubqueryCriterion(expression, true, Objects.requireNonNull(query, "query"), null, ignoreCase);
    }

    static InSubqueryCriterion notIn(QueryExpression expression, EntitySelect<?> query, boolean ignoreCase) {
        return new InSubqueryCriterion(expression, true, null, Objects.requireNonNull(query, "query"), ignoreCase);
    }
}
