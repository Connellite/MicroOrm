package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Native stored procedure or function call attached to an {@link io.github.connellite.microorm.repository.EntityRepository} method.
 * Void repository methods are treated as procedures; non-void methods are treated as scalar functions.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Procedure {
    /** Procedure or function name, or full native call SQL. Defaults to the repository method name. */
    String value() default "";

    /** Alias for {@link #value()} when a named attribute is clearer. */
    String procedureName() default "";

}
