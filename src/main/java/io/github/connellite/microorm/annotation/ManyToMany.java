package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Many-to-many association. Field type must be
 * {@link io.github.connellite.microorm.relation.LazyCollection} or
 * {@link io.github.connellite.microorm.relation.EagerCollection}.
 * <p>
 * The owning side declares {@link JoinTable} (or accepts generated defaults) and writes
 * the join table. The inverse side sets {@link #mappedBy()} to the owning field name
 * and does not write join rows.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManyToMany {
    /**
     * Owning-side field name on the target entity. Blank means this side owns the join table.
     */
    String mappedBy() default "";

    /** Operations cascaded to collection elements. Empty by default, like JPA. */
    CascadeType[] cascade() default {};
}
