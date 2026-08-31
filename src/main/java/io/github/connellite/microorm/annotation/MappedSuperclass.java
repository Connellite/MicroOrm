package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a superclass whose mapped fields are inherited by entity subclasses. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MappedSuperclass {
}
