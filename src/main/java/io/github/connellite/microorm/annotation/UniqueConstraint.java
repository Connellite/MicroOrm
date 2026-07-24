package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Table-level unique constraint declaration. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueConstraint {
    /** Constraint/index name. When blank, MicroOrm derives a deterministic name. */
    String name() default "";

    /** Column names participating in the constraint. */
    String[] columnNames();
}
