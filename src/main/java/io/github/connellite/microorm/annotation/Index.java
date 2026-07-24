package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Table index declaration, similar to JPA/Hibernate {@code @Index}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Index {
    /** Index name. When blank, MicroOrm derives a deterministic name. */
    String name() default "";

    /** Comma-separated column list, optionally with ordering keywords. */
    String columnList();

    /** Whether the index is unique. */
    boolean unique() default false;
}
