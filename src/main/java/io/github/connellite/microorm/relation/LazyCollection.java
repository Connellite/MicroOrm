package io.github.connellite.microorm.relation;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.microorm.mapping.OneToManyField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lazy one-to-many collection. Loaded on first {@link #get()} while the owning {@link io.github.connellite.microorm.session.Session}
 * is open.
 * <p>
 * For writes use {@link #of(List)} or {@link #empty()} before insert/update.
 */
public final class LazyCollection<T> {

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

    /** Wraps a collection loaded lazily from JDBC. */
    public static <T> LazyCollection<T> of(LazyLoadContext context, OneToManyField relation, Object ownerId) {
        Objects.requireNonNull(relation, "relation");
        return new LazyCollection<>(context, relation, ownerId, null);
    }

    /** Materialized collection for insert/update (inverse side is synced via {@code mappedBy}). */
    public static <T> LazyCollection<T> of(List<T> elements) {
        Objects.requireNonNull(elements, "elements");
        return new LazyCollection<>(null, null, null, List.copyOf(elements));
    }

    public static <T> LazyCollection<T> empty() {
        return new LazyCollection<>(null, null, null, List.of());
    }

    public static <T> void set(OneToManyField field, Object owner, LazyCollection<T> value) {
        MethodHandleReflectionUtil.set(field.varHandle(), field.javaField(), owner, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> LazyCollection<T> get(OneToManyField field, Object owner) {
        return (LazyCollection<T>) MethodHandleReflectionUtil.get(field.varHandle(), owner);
    }

    public List<T> get() {
        if (loaded == null) {
            LazyLoadContext.ensureOpen(context);
            loaded = context.loadCollection(relation, ownerId);
        }
        return loaded;
    }

    /** Returns materialized elements without loading from the database. */
    public List<T> elementsOrEmpty() {
        return loaded == null ? List.of() : loaded;
    }

    public boolean isMaterialized() {
        return loaded != null;
    }

    public boolean isLoaded() {
        return loaded != null && context != null;
    }

    public Object ownerId() {
        return ownerId;
    }

    /** Mutable builder for assembling a collection before persist. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private final List<T> items = new ArrayList<>();

        public Builder<T> add(T item) {
            items.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        public LazyCollection<T> build() {
            return LazyCollection.of(Collections.unmodifiableList(items));
        }
    }
}
