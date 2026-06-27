package io.github.connellite.microorm.query;

/** Predicate shape used by the SQL renderer for a {@link FieldCriterion}. */
public enum CriterionKind {
    /** Binary comparison such as {@code name = :p1}. */
    COMPARISON,
    /** Collection membership using SQL {@code IN}. */
    IN,
    /** Pattern match using SQL {@code LIKE}. */
    LIKE,
    /** SQL {@code IS NULL}. */
    IS_NULL,
    /** SQL {@code IS NOT NULL}. */
    IS_NOT_NULL
}
