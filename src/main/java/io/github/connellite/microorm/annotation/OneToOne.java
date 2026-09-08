package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One-to-one association. Field type must be {@link io.github.connellite.microorm.relation.LazyRef}
 * or {@link io.github.connellite.microorm.relation.EagerRef} with a type argument pointing at the
 * target {@link Entity}.
 * <p>
 * The owning side declares {@link JoinColumn} (or accepts {@code <fieldName>_id}) and stores the
 * foreign key. The inverse side sets {@link #mappedBy()} to the owning field name and does not
 * write a join column.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneToOne {
    /**
     * Owning-side field name on the target entity. Blank means this side owns the foreign key.
     */
    String mappedBy() default "";

    /** Operations cascaded to the referenced entity. Empty by default, like JPA. */
    CascadeType[] cascade() default {};

    /**
     * Whether the association is optional. {@code false} on the owning side means a persistent
     * target must always exist, like JPA {@code optional=false}.
     */
    boolean optional() default true;

    /**
     * When {@code true}, a related entity that disappears from a materialized reference
     * (or is deleted with the owner) is deleted. Independent of {@link #cascade()}, like JPA
     * {@code orphanRemoval}.
     */
    boolean orphanRemoval() default false;
}
