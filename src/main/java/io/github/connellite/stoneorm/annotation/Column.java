package io.github.connellite.stoneorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
    /** SQL column name; if blank, same as Java field name. */
    String name() default "";

    boolean nullable() default true;
}
