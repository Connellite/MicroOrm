package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.mapping.OneToManyField;
import io.github.connellite.reflection.MethodHandleReflectionUtil;

import java.util.List;

/**
 * Common contract for one-to-many relation wrappers.
 *
 * @param <T> child entity type
 */
public interface EntityCollection<T> {

    /**
     * Returns all child entities. Lazy implementations may load them on first access; eager implementations
     * return the already materialized list.
     */
    List<T> get();

    /** Returns elements already available in memory, or an empty list when not materialized. */
    List<T> elementsOrEmpty();

    /** {@code true} when the collection elements are available in memory. */
    boolean isMaterialized();

    /** {@code true} when child rows were loaded or eagerly materialized. */
    boolean isLoaded();

    /** Primary key of the owning entity when known. */
    Object ownerId();

    /** Reads a relation collection wrapper from a mapped entity field. */
    static EntityCollection<?> get(OneToManyField field, Object owner) {
        return (EntityCollection<?>) MethodHandleReflectionUtil.get(field.varHandle(), owner);
    }

    /** Sets a relation collection wrapper on a mapped entity field. */
    static void set(OneToManyField field, Object owner, EntityCollection<?> value) {
        MethodHandleReflectionUtil.set(field.varHandle(), field.javaField(), owner, value);
    }
}
