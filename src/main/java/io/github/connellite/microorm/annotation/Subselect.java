package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps an entity to a SQL subselect. Subselect entities are read-only and cannot be used for schema
 * generation or DML operations.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subselect {
    /** SQL select body used as the entity source. */
    String value();
}
