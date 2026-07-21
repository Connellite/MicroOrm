package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a mapped entity. Requires exactly one {@link Id} field and a no-arg constructor.
 * <p>
 * Use {@link Table} to set the physical table name/schema, {@link Immutable} for read-only entities,
 * or {@link Subselect} for entities backed by a SQL subselect.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {
}
