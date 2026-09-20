package io.github.connellite.microorm.query;

/**
 * One {@code ORDER BY} item for an {@link EntitySelect}.
 *
 * @param fieldName mapped Java field name, physical column name, or joined path ({@code relation.field})
 * @param direction sort direction; defaults to {@link OrderDirection#ASC} when {@code null}
 * @param ignoreCase whether to wrap the sort expression in {@code LOWER(...)}
 */
public record Order(String fieldName, OrderDirection direction, boolean ignoreCase) {

    public Order {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        direction = direction == null ? OrderDirection.ASC : direction;
    }

    /** Ascending or descending order without {@code LOWER(...)}. */
    public Order(String fieldName, OrderDirection direction) {
        this(fieldName, direction, false);
    }
}
