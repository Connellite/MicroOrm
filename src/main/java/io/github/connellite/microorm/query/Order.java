package io.github.connellite.microorm.query;

/**
 * One {@code ORDER BY} item for an {@link EntityQuery}.
 *
 * @param fieldName mapped Java field name or physical column name
 * @param direction sort direction
 */
public record Order(String fieldName, OrderDirection direction) {

    public Order {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        direction = direction == null ? OrderDirection.ASC : direction;
    }
}
