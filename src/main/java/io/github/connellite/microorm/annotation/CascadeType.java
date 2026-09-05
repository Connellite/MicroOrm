package io.github.connellite.microorm.annotation;

/**
 * JPA-style cascade operations for {@link ManyToOne}, {@link OneToOne}, {@link OneToMany},
 * and {@link ManyToMany}.
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
