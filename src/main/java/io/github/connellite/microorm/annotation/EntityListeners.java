package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Listener classes invoked around entity lifecycle callbacks.
 * Each class needs a public no-arg constructor. Callback methods take the entity as their only argument.
 * Listeners declared on superclasses are included; a subclass declaration does not replace them.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityListeners {

    Class<?>[] value();
}
