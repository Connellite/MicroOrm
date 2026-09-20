package io.github.connellite.microorm.query;

import io.github.connellite.microorm.sql.Query;

import java.util.Objects;

/**
 * Predicate comparing a mapped field to {@code IN (...)} or {@code NOT IN (...)} subquery result.
 *
 * @param fieldName mapped Java field name or physical column name
 * @param negated whether to render {@code NOT IN}
 * @param query raw subquery producing comparable values
 * @param entitySelect entity subquery producing comparable values
 * @param ignoreCase whether to wrap the compared column in {@code LOWER(...)}
 */
public record InSubqueryCriterion(
        String fieldName,
        boolean negated,
        Query query,
        EntitySelect<?> entitySelect,
        boolean ignoreCase) implements Criterion {

    public InSubqueryCriterion {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        if ((query == null) == (entitySelect == null)) {
            throw new IllegalArgumentException("Exactly one subquery must be provided");
        }
    }

    static InSubqueryCriterion in(String fieldName, Query query, boolean ignoreCase) {
        return new InSubqueryCriterion(fieldName, false, Objects.requireNonNull(query, "query"), null, ignoreCase);
    }

    static InSubqueryCriterion in(String fieldName, EntitySelect<?> query, boolean ignoreCase) {
        return new InSubqueryCriterion(fieldName, false, null, Objects.requireNonNull(query, "query"), ignoreCase);
    }

    static InSubqueryCriterion notIn(String fieldName, Query query, boolean ignoreCase) {
        return new InSubqueryCriterion(fieldName, true, Objects.requireNonNull(query, "query"), null, ignoreCase);
    }

    static InSubqueryCriterion notIn(String fieldName, EntitySelect<?> query, boolean ignoreCase) {
        return new InSubqueryCriterion(fieldName, true, null, Objects.requireNonNull(query, "query"), ignoreCase);
    }
}
