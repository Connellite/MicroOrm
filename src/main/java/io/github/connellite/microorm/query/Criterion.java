package io.github.connellite.microorm.query;

/**
 * Boolean expression used in an {@link EntityQuery} {@code WHERE} clause.
 * <p>
 * Instances are normally created through {@link EntityQuery#field(String)}:
 *
 * <pre>{@code
 * EntityQuery.of(User.class)
 *         .where(EntityQuery.field("name").like("Ada%"))
 *         .and(EntityQuery.field("enabled").eq(true));
 * }</pre>
 */
public sealed interface Criterion permits FieldCriterion, CompositeCriterion, NotCriterion {

    /**
     * Combines this criterion and {@code other} with SQL {@code AND}.
     *
     * @param other expression to combine with
     * @return combined criterion
     */
    default Criterion and(Criterion other) {
        return CompositeCriterion.and(this, other);
    }

    /**
     * Combines this criterion and {@code other} with SQL {@code OR}.
     *
     * @param other expression to combine with
     * @return combined criterion
     */
    default Criterion or(Criterion other) {
        return CompositeCriterion.or(this, other);
    }

    /**
     * Wraps this criterion with SQL {@code NOT}.
     *
     * @return negated criterion
     */
    default Criterion not() {
        return new NotCriterion(this);
    }
}
