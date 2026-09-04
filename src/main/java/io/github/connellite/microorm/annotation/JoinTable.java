package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Join table for the owning side of a {@link ManyToMany} association.
 * When omitted, defaults are {@code <ownerTable>_<targetTable>} with
 * {@code <ownerTable>_<ownerPk>} and {@code <targetTable>_<targetPk>} columns.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JoinTable {
    /** Join table name. Blank uses {@code <ownerTable>_<targetTable>}. */
    String name() default "";

    /** Optional schema/catalog of the join table. */
    String schema() default "";

    /** Foreign-key column(s) pointing at the owning entity. Only the first element is used. */
    JoinColumn[] joinColumns() default {};

    /** Foreign-key column(s) pointing at the target entity. Only the first element is used. */
    JoinColumn[] inverseJoinColumns() default {};
}
