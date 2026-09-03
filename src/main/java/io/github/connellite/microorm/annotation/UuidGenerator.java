package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates UUID primary keys before insert using MicroOrm's UUID generator integration.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UuidGenerator {

    Version version() default Version.VERSION_4;

    enum Version {
        VERSION_1(1),
        VERSION_4(4),
        VERSION_6(6),
        VERSION_7(7);

        private final int number;

        Version(int number) {
            this.number = number;
        }

        public int number() {
            return number;
        }
    }
}
