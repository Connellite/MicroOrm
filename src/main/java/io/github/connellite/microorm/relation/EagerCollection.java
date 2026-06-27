package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.mapping.OneToManyField;
import io.github.connellite.reflection.MethodHandleReflectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Eager one-to-many collection of related entities. Child rows are materialized when the owning
 * entity is hydrated, so {@link #get()} never performs a database round-trip.
 * <p>
 * For writes use {@link #of(List)}, {@link #empty()}, or {@link #builder()} before insert/update.
 */
public final class EagerCollection<T> implements EntityCollection<T> {

    private final Object ownerId;
    private final List<T> elements;

    private EagerCollection(Object ownerId, List<T> elements) {
        this.ownerId = ownerId;
        this.elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
    }

    /** Creates a materialized collection for insert/update. */
    public static <T> EagerCollection<T> of(List<T> elements) {
        return new EagerCollection<>(null, elements);
    }

    /** Creates a materialized collection for an already hydrated owner. */
    public static <T> EagerCollection<T> of(Object ownerId, List<T> elements) {
        return new EagerCollection<>(ownerId, elements);
    }

    /** Creates an empty materialized collection for insert/update. */
    public static <T> EagerCollection<T> empty() {
        return new EagerCollection<>(null, List.of());
    }

    /** Sets an {@link EagerCollection} on an entity field (VarHandle helper for mapped collection fields). */
    public static <T> void set(OneToManyField field, Object owner, EagerCollection<T> value) {
        MethodHandleReflectionUtil.set(field.varHandle(), field.javaField(), owner, value);
    }

    /** Reads an {@link EagerCollection} from an entity field (VarHandle helper for mapped collection fields). */
    @SuppressWarnings("unchecked")
    public static <T> EagerCollection<T> get(OneToManyField field, Object owner) {
        return (EagerCollection<T>) MethodHandleReflectionUtil.get(field.varHandle(), owner);
    }

    /** Returns all child entities already materialized in memory. */
    public List<T> get() {
        return elements;
    }

    /** Returns elements already in memory. Never loads from the database. */
    public List<T> elementsOrEmpty() {
        return elements;
    }

    /** Always {@code true}: eager collections are materialized at construction time. */
    public boolean isMaterialized() {
        return true;
    }

    /** Always {@code true}: eager collections never defer loading. */
    public boolean isLoaded() {
        return true;
    }

    /**
     * Primary key of the owning entity used to query children during eager hydration ({@code null} for
     * collections built only for persist).
     */
    public Object ownerId() {
        return ownerId;
    }

    /** Starts a mutable builder for assembling a collection before persist. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** Mutable builder that produces an {@link #of(List)} collection. */
    public static final class Builder<T> {
        private final List<T> items = new ArrayList<>();

        /** Appends a non-null element. */
        public Builder<T> add(T item) {
            items.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        /** Builds an immutable materialized collection for insert/update. */
        public EagerCollection<T> build() {
            return EagerCollection.of(Collections.unmodifiableList(items));
        }
    }
}
