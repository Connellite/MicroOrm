package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a mapped entity. Requires exactly one {@link Id} field and a no-arg constructor.
 * Table name defaults to the class simple name (lower-cased, then optionally transformed by
 * {@link io.github.connellite.microorm.mapping.PhysicalNamingStrategy}). When {@link #schema()} is set,
 * generated SQL and schema operations address the table as {@code schema.table}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {
    /** Logical table name; if blank, derived from the class simple name in lowercase. */
    String name() default "";

    /**
     * Database schema/catalog name to qualify this entity table with.
     * <p>
     * Leave blank to use the connection's default schema. The value supports the same backtick quoting
     * convention as {@link #name()} for case-sensitive or reserved identifiers.
     */
    String schema() default "";
}
