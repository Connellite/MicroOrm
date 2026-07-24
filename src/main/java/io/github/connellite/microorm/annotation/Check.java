package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** SQL check constraint for an entity table or a column. */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Checks.class)
public @interface Check {
    /** Optional constraint name. */
    String name() default "";

    /** SQL check expression without the surrounding {@code CHECK (...)}. */
    String constraints();
}
