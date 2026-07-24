package io.github.connellite.microorm.query;

/**
 * Boolean expression used in an {@link EntitySelect} {@code WHERE} clause.
 * <p>
 * Instances are normally created through {@link EntitySelect#field(String)}:
 *
 * <pre>{@code
 * EntitySelect.of(User.class)
 *         .where(EntitySelect.field("name").like("Ada%"))
 *         .and(EntitySelect.field("enabled").eq(true));
 * }</pre>
 */
public sealed interface Criterion
        permits FieldCriterion, CompositeCriterion, NotCriterion, ExistsCriterion, QuantifiedSubqueryCriterion {

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
