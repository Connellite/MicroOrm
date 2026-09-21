package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.mapping.CollectionRelation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Lazy one-to-many collection of related entities. Child rows are loaded on first {@link #get()}
 * while the owning {@link io.github.connellite.microorm.session.Session} is open.
 * <p>
 * For writes use {@link #of(Collection)}, {@link #of(Object[])}, {@link #empty()}, or {@link #builder()}
 * before insert/update.
 */
public final class LazyCollection<T> extends EntityCollection<T> {

    private final LazyLoadContext context;
    private final CollectionRelation relation;

    private LazyCollection(LazyLoadContext context, CollectionRelation relation, Object ownerId, Collection<? extends T> loaded) {
        super(ownerId, loaded, false);
        this.context = context;
        this.relation = relation;
    }

    /**
     * Creates a lazy collection for the inverse side of a {@code mappedBy} relation.
     * Children are queried by {@code ownerId} on first {@link #get()}.
     */
    public static <T> LazyCollection<T> of(LazyLoadContext context, CollectionRelation relation, Object ownerId) {
        Objects.requireNonNull(relation, "relation");
        return new LazyCollection<>(context, relation, ownerId, null);
    }

    /**
     * Creates a materialized collection for insert/update. The inverse {@link LazyRef} on each child
     * is synced when the graph is persisted.
     */
    public static <T> LazyCollection<T> of(Collection<? extends T> elements) {
        Objects.requireNonNull(elements, "elements");
        return new LazyCollection<>(null, null, null, elements);
    }

    /**
     * Creates a materialized collection for insert/update from individual child entities.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> LazyCollection<T> of(T... elements) {
        Objects.requireNonNull(elements, "elements");
        return of(List.of(elements));
    }

    /** Creates an empty materialized collection for insert/update. */
    public static <T> LazyCollection<T> empty() {
        return new LazyCollection<>(null, null, null, List.of());
    }

    /** Sets a {@link LazyCollection} on an entity field (VarHandle helper for mapped collection fields). */
    public static <T> void set(CollectionRelation field, Object owner, LazyCollection<T> value) {
        EntityCollection.set(field, owner, value);
    }

    /** Reads a {@link LazyCollection} from an entity field (VarHandle helper for mapped collection fields). */
    @SuppressWarnings("unchecked")
    public static <T> LazyCollection<T> get(CollectionRelation field, Object owner) {
        return (LazyCollection<T>) EntityCollection.get(field, owner);
    }

    /**
     * Returns all child entities, loading them on first access when this collection was created with
     * {@link #of(LazyLoadContext, CollectionRelation, Object)}.
     */
    @Override
    public List<T> get() {
        if (!isMaterialized()) {
            LazyLoadContext.ensureOpen(context);
            materialize(context.loadCollection(relation, ownerId()));
        }
        return elementsOrEmpty();
    }

    /** Starts a mutable builder for assembling a collection before persist. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** Mutable builder that produces a {@link #of(Collection)} collection. */
    public static final class Builder<T> {
        private final List<T> items = new ArrayList<>();

        /** Appends a non-null element. */
        public Builder<T> add(T item) {
            items.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        /** Appends all elements from the given collection. */
        public Builder<T> addAll(Collection<? extends T> more) {
            Objects.requireNonNull(more, "items");
            for (T item : more) {
                add(item);
            }
            return this;
        }

        /** Appends individual non-null elements. */
        @SafeVarargs
        @SuppressWarnings("varargs")
        public final Builder<T> add(T... more) {
            Objects.requireNonNull(more, "items");
            return addAll(List.of(more));
        }

        /** Builds an immutable materialized collection for insert/update. */
        public LazyCollection<T> build() {
            return LazyCollection.of(items);
        }
    }
}
