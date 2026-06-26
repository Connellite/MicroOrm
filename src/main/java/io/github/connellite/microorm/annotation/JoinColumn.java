package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Foreign-key column for a {@link ManyToOne} association. Stored on the owning entity table;
 * the referenced entity is loaded lazily via {@link io.github.connellite.microorm.relation.LazyRef}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JoinColumn {
    /** Column name; when blank, defaults to {@code <fieldName>_id}. */
    String name() default "";

    boolean nullable() default true;
}
