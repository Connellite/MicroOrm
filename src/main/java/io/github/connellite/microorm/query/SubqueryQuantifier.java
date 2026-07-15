package io.github.connellite.microorm.query;

/** SQL quantifier for comparisons against a subquery result. */
public enum SubqueryQuantifier {
    /** SQL {@code ANY}. */
    ANY,
    /** SQL {@code ALL}. */
    ALL
}
