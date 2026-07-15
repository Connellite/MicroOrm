package io.github.connellite.microorm.query;

/** Predicate shape used by the SQL renderer for a {@link FieldCriterion}. */
public enum CriterionKind {
    /** Binary comparison such as {@code name = :p1}. */
    COMPARISON,
    /** Collection membership using SQL {@code IN}. */
    IN,
    /** Collection exclusion using SQL {@code NOT IN}. */
    NOT_IN,
    /** Pattern match using SQL {@code LIKE}. */
    LIKE,
    /** Negative pattern match using SQL {@code NOT LIKE}. */
    NOT_LIKE,
    /** Inclusive range match using SQL {@code BETWEEN}. */
    BETWEEN,
    /** Inclusive range exclusion using SQL {@code NOT BETWEEN}. */
    NOT_BETWEEN,
    /** SQL {@code IS NULL}. */
    IS_NULL,
    /** SQL {@code IS NOT NULL}. */
    IS_NOT_NULL
}
