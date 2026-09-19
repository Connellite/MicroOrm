package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps {@link java.util.Date} or {@link java.util.Calendar} to a SQL date, time, or timestamp column.
 * Required on those field types, matching JPA.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Temporal {
    TemporalType value();
}
