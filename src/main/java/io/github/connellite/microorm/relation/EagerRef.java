package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.mapping.ManyToOneField;

import java.util.Objects;

/**
 * Eager many-to-one reference to a related entity. The target row is materialized when the owning
 * entity is hydrated, so {@link #get()} never performs a database round-trip.
 * <p>
 * For writes use {@link #to(Object)} to reference a managed or new entity, or {@link #toId(Class, Object)}
 * to reference an existing row by primary key without loading it.
 */
public final class EagerRef<T> extends EntityRef<T> {

    private EagerRef(Class<T> targetType, Object foreignKey, T loaded) {
        super(Objects.requireNonNull(targetType, "targetType"), foreignKey, loaded);
    }

    /**
     * Creates an eager reference from a join-column value and an already materialized target entity.
     * Pass {@code null} for both {@code foreignKey} and {@code loaded} for a nullable SQL {@code NULL}
     * foreign key.
     */
    public static <T> EagerRef<T> of(Class<?> targetType, Object foreignKey, Object loaded) {
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) targetType;
        @SuppressWarnings("unchecked")
        T entity = (T) loaded;
        return new EagerRef<>(type, foreignKey, entity);
    }

    /**
     * Creates a reference to an entity instance for insert/update (the join column is taken from its primary key
     * when the graph is persisted).
     */
    public static <T> EagerRef<T> to(T entity) {
        Objects.requireNonNull(entity, "entity");
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) entity.getClass();
        return new EagerRef<>(type, null, entity);
    }

    /**
     * Creates a reference to an existing row by primary key without loading it (write or read-by-id only).
     * Use {@link #foreignKey()} to read the raw id.
     */
    public static <T> EagerRef<T> toId(Class<T> targetType, Object id) {
        return new EagerRef<>(targetType, id, null);
    }

    /** Sets an {@link EagerRef} on an entity field (VarHandle helper for mapped {@code EagerRef} fields). */
    public static <T> void set(ManyToOneField field, Object owner, EagerRef<T> value) {
        EntityRef.set(field, owner, value);
    }

    /** Reads an {@link EagerRef} from an entity field (VarHandle helper for mapped {@code EagerRef} fields). */
    @SuppressWarnings("unchecked")
    public static <T> EagerRef<T> get(ManyToOneField field, Object owner) {
        return (EagerRef<T>) EntityRef.get(field, owner);
    }

    /**
     * Returns the related entity already materialized in memory, or {@code null} for SQL {@code NULL}
     * / id-only references created with {@link #toId(Class, Object)}.
     */
    @Override
    public T get() {
        return attachedEntity();
    }
}
