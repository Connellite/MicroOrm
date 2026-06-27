package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One-to-many association (inverse side). Field type must be
 * {@link io.github.connellite.microorm.relation.LazyCollection} or
 * {@link io.github.connellite.microorm.relation.EagerCollection} with the child entity as type argument.
 * {@link #mappedBy()} names the {@link ManyToOne} field on the child that owns the foreign key.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneToMany {
    /** Name of the {@link ManyToOne} field on the child entity that points back to this entity. */
    String mappedBy();
}
