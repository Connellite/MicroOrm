package io.github.connellite.microorm.annotation;

import io.github.connellite.microorm.type.JdbcType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects the SQL column type from {@link JdbcType#sqlType}. The Java field type is unchanged.
 * {@link Column#sqlType()} overrides this when it is set.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Type {

    Class<? extends JdbcType> value();
}
