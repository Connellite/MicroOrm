package io.github.connellite.microorm.annotation;

import io.github.connellite.microorm.type.AttributeConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Applies an {@link AttributeConverter} to a mapped scalar field. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Convert {
    /** Converter class with a no-arg constructor. */
    Class<? extends AttributeConverter<?, ?>> converter();
}
