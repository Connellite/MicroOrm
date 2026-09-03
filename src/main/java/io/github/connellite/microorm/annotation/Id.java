package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Primary key field. Supported types: numeric wrappers/primitives and {@link java.util.UUID}.
 * UUID keys are generated on insert when unset; numeric database-generated keys use {@link GeneratedValue}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Id {
}
