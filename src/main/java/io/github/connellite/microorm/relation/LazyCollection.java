package io.github.connellite.microorm.relation;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.microorm.mapping.OneToManyField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lazy one-to-many collection of related entities. Child rows are loaded on first {@link #get()}
 * while the owning {@link io.github.connellite.microorm.session.Session} is open.
 * <p>
 * For writes use {@link #of(List)}, {@link #empty()}, or {@link #builder()} before insert/update.
 */
public final class LazyCollection<T> implements EntityCollection<T> {

    private final LazyLoadContext context;
    private final OneToManyField relation;
    private final Object ownerId;
    private List<T> loaded;

    private LazyCollection(LazyLoadContext context, OneToManyField relation, Object ownerId, List<T> loaded) {
        this.context = context;
        this.relation = relation;
        this.ownerId = ownerId;
        this.loaded = loaded;
    }

    /**
     * Creates a lazy collection for the inverse side of a {@code mappedBy} relation.
     * Children are queried by {@code ownerId} on first {@link #get()}.
     */
    public static <T> LazyCollection<T> of(LazyLoadContext context, OneToManyField relation, Object ownerId) {
        Objects.requireNonNull(relation, "relation");
        return new LazyCollection<>(context, relation, ownerId, null);
    }

    /**
     * Creates a materialized collection for insert/update. The inverse {@link LazyRef} on each child
     * is synced when the graph is persisted.
     */
    public static <T> LazyCollection<T> of(List<T> elements) {
        Objects.requireNonNull(elements, "elements");
        return new LazyCollection<>(null, null, null, List.copyOf(elements));
    }

    /** Creates an empty materialized collection for insert/update. */
    public static <T> LazyCollection<T> empty() {
        return new LazyCollection<>(null, null, null, List.of());
    }

    /** Sets a {@link LazyCollection} on an entity field (VarHandle helper for mapped collection fields). */
    public static <T> void set(OneToManyField field, Object owner, LazyCollection<T> value) {
        MethodHandleReflectionUtil.set(field.varHandle(), field.javaField(), owner, value);
    }

    /** Reads a {@link LazyCollection} from an entity field (VarHandle helper for mapped collection fields). */
    @SuppressWarnings("unchecked")
    public static <T> LazyCollection<T> get(OneToManyField field, Object owner) {
        return (LazyCollection<T>) MethodHandleReflectionUtil.get(field.varHandle(), owner);
    }

    /**
     * Returns all child entities, loading them on first access when this collection was created with
     * {@link #of(LazyLoadContext, OneToManyField, Object)}.
     */
    public List<T> get() {
        if (loaded == null) {
            LazyLoadContext.ensureOpen(context);
            loaded = context.loadCollection(relation, ownerId);
        }
        return loaded;
    }

    /**
     * Returns elements already in memory, or an empty list if not yet loaded or built for persist.
     * Never loads from the database.
     */
    public List<T> elementsOrEmpty() {
        return loaded == null ? List.of() : loaded;
    }

    /**
     * {@code true} when the list is available in memory (including {@link #of(List)} / {@link #empty()}),
     * without implying a database load occurred.
     */
    public boolean isMaterialized() {
        return loaded != null;
    }

    /**
     * {@code true} when child rows were loaded from the database via {@link #get()}
     * (lazy collection created with {@link #of(LazyLoadContext, OneToManyField, Object)}).
     */
    public boolean isLoaded() {
        return loaded != null && context != null;
    }

    /**
     * Primary key of the owning entity used to query children on lazy load ({@code null} for
     * collections built only for persist).
     */
    public Object ownerId() {
        return ownerId;
    }

    /** Starts a mutable builder for assembling a collection before persist. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** Mutable builder that produces a {@link #of(List)} collection. */
    public static final class Builder<T> {
        private final List<T> items = new ArrayList<>();

        /** Appends a non-null element. */
        public Builder<T> add(T item) {
            items.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        /** Builds an immutable materialized collection for insert/update. */
        public LazyCollection<T> build() {
            return LazyCollection.of(Collections.unmodifiableList(items));
        }
    }
}
