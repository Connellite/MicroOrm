package io.github.connellite.microorm.query;

/** Logical operator used between child criteria in a {@link CompositeCriterion}. */
public enum CompositeOperator {
    /** SQL {@code AND}. */
    AND,
    /** SQL {@code OR}. */
    OR
}
