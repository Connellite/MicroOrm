package io.github.connellite.microorm.annotation;

/**
 * JPA-style cascade operations for {@link ManyToOne} and {@link OneToMany}.
 * Defaults are empty: associations are not cascaded unless declared.
 */
public enum CascadeType {
    ALL,
    PERSIST,
    MERGE,
    REMOVE,
    REFRESH,
    DETACH
}
