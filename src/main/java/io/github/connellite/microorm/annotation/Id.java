package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Primary key field. Supported types: numeric wrappers/primitives and {@link java.util.UUID}.
 * UUID keys are generated on insert when unset; numeric keys require {@link #autoIncrement()}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Id {
    /** When true, the primary key column is omitted on insert and filled from generated keys. */
    boolean autoIncrement() default false;
}
