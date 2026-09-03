package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a named sequence generator for an {@link Id} field. */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SequenceGenerator {
    /** Generator name referenced by {@link GeneratedValue#generator()}. */
    String name() default "";

    /** Physical sequence name. When blank, MicroOrm derives a name from table and id column. */
    String sequenceName() default "";

    /** Sequence increment size. */
    int allocationSize() default 1;

    /** First sequence value. */
    int initialValue() default 1;
}
