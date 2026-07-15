package io.github.connellite.microorm.query;

import io.github.connellite.microorm.sql.Query;

import java.util.Objects;

/**
 * SQL {@code EXISTS (...)} or {@code NOT EXISTS (...)} predicate.
 *
 * @param query raw subquery to render inside {@code EXISTS}
 * @param entityQuery entity subquery to render inside {@code EXISTS}
 * @param negated whether to render {@code NOT EXISTS}
 */
public record ExistsCriterion(Query query, EntityQuery<?> entityQuery, boolean negated) implements Criterion {

    public ExistsCriterion {
        if ((query == null) == (entityQuery == null)) {
            throw new IllegalArgumentException("Exactly one subquery must be provided");
        }
    }

    static ExistsCriterion exists(Query query) {
        return new ExistsCriterion(Objects.requireNonNull(query, "query"), null, false);
    }

    static ExistsCriterion exists(EntityQuery<?> query) {
        return new ExistsCriterion(null, Objects.requireNonNull(query, "query"), false);
    }

    static ExistsCriterion notExists(Query query) {
        return new ExistsCriterion(Objects.requireNonNull(query, "query"), null, true);
    }

    static ExistsCriterion notExists(EntityQuery<?> query) {
        return new ExistsCriterion(null, Objects.requireNonNull(query, "query"), true);
    }
}
