package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Native SQL query attached to an {@link io.github.connellite.microorm.repository.EntityRepository} method. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Query {
    /** SQL text with {@code :name} named placeholders. */
    String value();
}
