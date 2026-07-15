package io.github.connellite.microorm.query;

import io.github.connellite.microorm.sql.Query;

import java.util.Objects;

/**
 * Predicate comparing a mapped field to {@code ANY (...)} or {@code ALL (...)} subquery result.
 *
 * @param fieldName mapped Java field name or physical column name
 * @param operator comparison operator before the quantifier
 * @param quantifier SQL quantifier
 * @param query raw subquery producing comparable values
 * @param entityQuery entity subquery producing comparable values
 */
public record QuantifiedSubqueryCriterion(
        String fieldName,
        ComparisonOperator operator,
        SubqueryQuantifier quantifier,
        Query query,
        EntityQuery<?> entityQuery) implements Criterion {

    public QuantifiedSubqueryCriterion {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(quantifier, "quantifier");
        if ((query == null) == (entityQuery == null)) {
            throw new IllegalArgumentException("Exactly one subquery must be provided");
        }
    }
}
