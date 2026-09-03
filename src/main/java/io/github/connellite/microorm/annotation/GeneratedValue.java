package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an {@link Id} field as database-generated. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GeneratedValue {
    /** Generation strategy. Defaults to identity/autoincrement. */
    GenerationType strategy() default GenerationType.IDENTITY;

    /** Optional named generator declared with {@link SequenceGenerator} or {@link GenericGenerator}. */
    String generator() default "";
}
