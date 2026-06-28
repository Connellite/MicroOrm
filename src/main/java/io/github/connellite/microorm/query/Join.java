package io.github.connellite.microorm.query;

/**
 * Join declaration for a root entity relation.
 * <p>
 * When {@code type} is {@code null}, {@link JoinType#INNER} is used.
 *
 * @param relationName Java field name of a {@code @ManyToOne} or {@code @OneToMany} relation
 * @param type SQL join type
 */
public record Join(String relationName, JoinType type) {

    public Join {
        if (relationName == null || relationName.isBlank()) {
            throw new IllegalArgumentException("relationName cannot be blank");
        }
        type = type == null ? JoinType.INNER : type;
    }
}
