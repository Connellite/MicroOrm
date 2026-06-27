package io.github.connellite.microorm.query;

/** Comparison operators supported by {@link FieldPath}. */
public enum ComparisonOperator {
    /** SQL {@code =}. */
    EQ("="),
    /** SQL {@code <>}. */
    NE("<>"),
    /** SQL {@code <}. */
    LT("<"),
    /** SQL {@code <=}. */
    LE("<="),
    /** SQL {@code >}. */
    GT(">"),
    /** SQL {@code >=}. */
    GE(">=");

    private final String sql;

    ComparisonOperator(String sql) {
        this.sql = sql;
    }

    /** SQL token for this operator. */
    public String sql() {
        return sql;
    }
}
