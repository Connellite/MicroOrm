package io.github.connellite.stoneorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Id {
    /** When true, column is omitted on insert and filled from generated keys (SQLite INTEGER PRIMARY KEY AUTOINCREMENT). */
    boolean autoIncrement() default false;
}
