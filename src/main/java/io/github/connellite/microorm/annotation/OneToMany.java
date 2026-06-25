package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Lazy one-to-many association; field type must be {@link io.github.connellite.microorm.relation.LazyCollection}. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneToMany {
    /** Name of the {@link ManyToOne} field on the child entity that points back to this entity. */
    String mappedBy();
}
