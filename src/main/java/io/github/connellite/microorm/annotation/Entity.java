package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a mapped entity. Requires exactly one {@link Id} field and a no-arg constructor.
 * Table name defaults to the class simple name (lower-cased, then optionally transformed by
 * {@link io.github.connellite.microorm.mapping.PhysicalNamingStrategy}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {
    /** Logical table name; if blank, derived from the class simple name in lowercase. */
    String name() default "";
}
