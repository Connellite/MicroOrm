package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.reflection.MethodHandleReflectionUtil;

/**
 * Base class for many-to-one relation wrappers declared on {@code @ManyToOne} fields.
 * <p>
 * Concrete types are {@link LazyRef} (loads the target on first {@link #get()} while the owning
 * {@link io.github.connellite.microorm.session.Session} is open) and {@link EagerRef} (target row
 * materialized when the owner is hydrated).
 *
 * @param <T> target entity type
 */
public abstract class EntityRef<T> {

    private final Class<T> targetType;
    private final Object foreignKey;
    private T loaded;

    protected EntityRef(Class<T> targetType, Object foreignKey, T loaded) {
        this.targetType = targetType;
        this.foreignKey = foreignKey;
        this.loaded = loaded;
    }

    protected final void attach(T entity) {
        loaded = entity;
    }

    /**
     * Returns the related entity. Lazy implementations may load it on first access; eager implementations
     * return the already materialized entity.
     */
    public abstract T get();

    /** Returns the in-memory entity without triggering a lazy load. */
    public T attachedEntity() {
        return loaded;
    }

    /** {@code true} when the target entity is already available in memory. */
    public boolean isLoaded() {
        return loaded != null;
    }

    /** {@code true} when this reference points at a foreign key or an attached entity. */
    public boolean isSet() {
        return foreignKey != null || loaded != null;
    }

    /** {@code true} when this reference has neither a foreign key nor an attached entity. */
    public boolean isNull() {
        return !isSet();
    }

    /** Raw join-column value when known without reading the target entity primary key. */
    public Object foreignKey() {
        return foreignKey;
    }

    /** Target entity class declared on the owning relation field. */
    public Class<T> targetType() {
        return targetType;
    }

    /** Reads a relation reference wrapper from a mapped entity field. */
    @SuppressWarnings("unchecked")
    public static <T> EntityRef<T> get(ManyToOneField field, Object owner) {
        return (EntityRef<T>) MethodHandleReflectionUtil.get(field.varHandle(), owner);
    }

    /** Sets a relation reference wrapper on a mapped entity field. */
    public static void set(ManyToOneField field, Object owner, EntityRef<?> value) {
        MethodHandleReflectionUtil.set(field.varHandle(), field.javaField(), owner, value);
    }
}
