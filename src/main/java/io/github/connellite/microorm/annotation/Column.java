package io.github.connellite.microorm.annotation;

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

    boolean unique() default false;

    boolean indexed() default false;

    /** Explicit SQL column type. If blank, MicroOrm infers a type from the Java field. */
    String sqlType() default "";

    /** Optional logical length metadata (for example VARCHAR length). */
    int length() default 0;
}
