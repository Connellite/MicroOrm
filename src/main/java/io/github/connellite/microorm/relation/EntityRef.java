package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.reflection.MethodHandleReflectionUtil;

/**
 * Common contract for many-to-one relation wrappers.
 *
 * @param <T> target entity type
 */
public interface EntityRef<T> {

    /**
     * Returns the related entity. Lazy implementations may load it on first access; eager implementations
     * return the already materialized entity.
     */
    T get();

    /** Returns the in-memory entity without triggering a lazy load. */
    T attachedEntity();

    /** {@code true} when the target entity is already available in memory. */
    boolean isLoaded();

    /** {@code true} when this reference points at a foreign key or an attached entity. */
    boolean isSet();

    /** {@code true} when this reference has neither a foreign key nor an attached entity. */
    boolean isNull();

    /** Raw join-column value when known without reading the target entity primary key. */
    Object foreignKey();

    /** Target entity class declared on the owning relation field. */
    Class<T> targetType();

    /** Reads a relation reference wrapper from a mapped entity field. */
    static EntityRef<?> get(ManyToOneField field, Object owner) {
        return (EntityRef<?>) MethodHandleReflectionUtil.get(field.varHandle(), owner);
    }

    /** Sets a relation reference wrapper on a mapped entity field. */
    static void set(ManyToOneField field, Object owner, EntityRef<?> value) {
        MethodHandleReflectionUtil.set(field.varHandle(), field.javaField(), owner, value);
    }
}
