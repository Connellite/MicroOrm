package io.github.connellite.microorm.query;

/**
 * One {@code ORDER BY} item for an {@link EntityQuery}.
 *
 * @param fieldName mapped Java field name, physical column name, or joined path ({@code relation.field})
 * @param direction sort direction; defaults to {@link OrderDirection#ASC} when {@code null}
 */
public record Order(String fieldName, OrderDirection direction) {

    public Order {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        direction = direction == null ? OrderDirection.ASC : direction;
    }
}
