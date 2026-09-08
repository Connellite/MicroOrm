package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Primary key field. Supported types: numeric wrappers/primitives, {@link java.util.UUID},
 * and assigned {@link String}.
 * UUID keys are generated on insert when unset; numeric database-generated keys use {@link GeneratedValue}.
 * A {@link String} id is never generated: it must be set to a non-blank value before persist
 * (for example a unique login assigned after the entity is constructed).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Id {
}