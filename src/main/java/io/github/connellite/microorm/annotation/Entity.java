package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or package as containing mapped entities. Each entity requires exactly one {@link Id} field
 * and a no-arg constructor.
 * <p>
 * Use {@link Table} to set the physical table name/schema, {@link Immutable} for read-only entities,
 * or {@link Subselect} for entities backed by a SQL subselect.
 */
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {
}
