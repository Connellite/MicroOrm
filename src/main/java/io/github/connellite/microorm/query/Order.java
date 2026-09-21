package io.github.connellite.microorm.query;

import java.util.Objects;

/**
 * One {@code ORDER BY} item for an {@link EntitySelect}.
 *
 * @param expression mapped field or scalar function expression
 * @param direction sort direction; defaults to {@link OrderDirection#ASC} when {@code null}
 * @param ignoreCase whether to wrap the sort expression in {@code LOWER(...)}
 */
public record Order(QueryExpression expression, OrderDirection direction, boolean ignoreCase) {

    public Order {
        Objects.requireNonNull(expression, "expression");
        direction = direction == null ? OrderDirection.ASC : direction;
    }

    /** Ascending or descending order on a mapped field without {@code LOWER(...)}. */
    public Order(String fieldName, OrderDirection direction) {
        this(new QueryExpression.Field(fieldName), direction, false);
    }

    /** Sort on a mapped field name or joined path. */
    public Order(String fieldName, OrderDirection direction, boolean ignoreCase) {
        this(new QueryExpression.Field(fieldName), direction, ignoreCase);
    }

    /** Mapped field or column name when this order is a field; otherwise a label. */
    public String fieldName() {
        return expression.describe();
    }
}
