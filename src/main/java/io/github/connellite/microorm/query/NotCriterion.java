package io.github.connellite.microorm.query;

import java.util.Objects;

/** Criterion that negates another expression with SQL {@code NOT}. */
public record NotCriterion(Criterion criterion) implements Criterion {

    public NotCriterion {
        Objects.requireNonNull(criterion, "criterion");
    }
}
