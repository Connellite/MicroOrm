package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.mapping.OneToManyField;
import io.github.connellite.reflection.MethodHandleReflectionUtil;

import java.util.List;

/**
 * Base class for one-to-many relation wrappers.
 *
 * @param <T> child entity type
 */
public abstract class EntityCollection<T> {

    private final Object ownerId;
    private List<T> elements;
    private boolean loaded;

    protected EntityCollection(Object ownerId, List<T> elements, boolean loaded) {
        this.ownerId = ownerId;
        this.elements = elements == null ? null : List.copyOf(elements);
        this.loaded = loaded;
    }

    protected final void materialize(List<T> elements) {
        this.elements = List.copyOf(elements);
        this.loaded = true;
    }

    /**
     * Returns all child entities. Lazy implementations may load them on first access; eager implementations
     * return the already materialized list.
     */
    public abstract List<T> get();

    /** Returns elements already available in memory, or an empty list when not materialized. */
    public List<T> elementsOrEmpty() {
        return elements == null ? List.of() : elements;
    }

    /** {@code true} when the collection elements are available in memory. */
    public boolean isMaterialized() {
        return elements != null;
    }

    /** {@code true} when child rows were loaded or eagerly materialized. */
    public boolean isLoaded() {
        return loaded;
    }

    /** Primary key of the owning entity when known. */
    public Object ownerId() {
        return ownerId;
    }

    /** Reads a relation collection wrapper from a mapped entity field. */
    @SuppressWarnings("unchecked")
    public static <T> EntityCollection<T> get(OneToManyField field, Object owner) {
        return (EntityCollection<T>) MethodHandleReflectionUtil.get(field.varHandle(), owner);
    }

    /** Sets a relation collection wrapper on a mapped entity field. */
    public static void set(OneToManyField field, Object owner, EntityCollection<?> value) {
        MethodHandleReflectionUtil.set(field.varHandle(), field.javaField(), owner, value);
    }
}
