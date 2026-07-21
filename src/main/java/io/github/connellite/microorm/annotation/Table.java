package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the physical table for an {@link Entity}. When absent, the table name is derived from the
 * class simple name and the configured physical naming strategy.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {
    /** Logical table name; if blank, derived from the class simple name. */
    String name() default "";

    /** Optional database schema/catalog name. */
    String schema() default "";
}
