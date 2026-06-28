package io.github.connellite.microorm.query;

/** SQL join types supported by {@link EntityQuery}. */
public enum JoinType {
    /** SQL {@code INNER JOIN}. */
    INNER("INNER JOIN"),
    /** SQL {@code LEFT JOIN}. */
    LEFT("LEFT JOIN"),
    /** SQL {@code RIGHT JOIN}. */
    RIGHT("RIGHT JOIN"),
    /** SQL {@code FULL JOIN}. */
    FULL("FULL JOIN"),
    /** SQL {@code CROSS JOIN}. */
    CROSS("CROSS JOIN");

    private final String sql;

    JoinType(String sql) {
        this.sql = sql;
    }

    /** SQL token used when rendering this join type. */
    public String sql() {
        return sql;
    }
}
