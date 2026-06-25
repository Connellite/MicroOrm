package io.github.connellite.microorm.relation;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.microorm.mapping.ManyToOneField;

import java.util.Objects;

/**
 * Lazy many-to-one reference. Loaded on first {@link #get()} while the owning {@link io.github.connellite.microorm.session.Session}
 * is open.
 * <p>
 * For writes use {@link #to(Object)} to reference a managed or new entity, or {@link #toId(Class, Object)} for an existing id.
 */
public final class LazyRef<T> {

    private final LazyLoadContext context;
    private final Class<T> targetType;
    private final Object foreignKey;
    private T loaded;

    private LazyRef(LazyLoadContext context, Class<T> targetType, Object foreignKey, T loaded) {
        this.context = context;
        this.targetType = targetType;
        this.foreignKey = foreignKey;
        this.loaded = loaded;
    }

    /** Wraps a foreign key loaded from JDBC (lazy read). */
    public static <T> LazyRef<T> of(LazyLoadContext context, Class<T> targetType, Object foreignKey) {
        Objects.requireNonNull(targetType, "targetType");
        return new LazyRef<>(context, targetType, foreignKey, null);
    }

    /** References an entity instance (insert/update graph). */
    public static <T> LazyRef<T> to(T entity) {
        Objects.requireNonNull(entity, "entity");
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) entity.getClass();
        return new LazyRef<>(null, type, null, entity);
    }

    /** References an existing row by primary key without loading it. */
    public static <T> LazyRef<T> toId(Class<T> targetType, Object id) {
        Objects.requireNonNull(targetType, "targetType");
        return new LazyRef<>(null, targetType, id, null);
    }

    public static <T> void set(ManyToOneField field, Object owner, LazyRef<T> value) {
        MethodHandleReflectionUtil.set(field.varHandle(), field.javaField(), owner, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> LazyRef<T> get(ManyToOneField field, Object owner) {
        return (LazyRef<T>) MethodHandleReflectionUtil.get(field.varHandle(), owner);
    }

    public T get() {
        if (loaded != null) {
            return loaded;
        }
        if (foreignKey == null) {
            return null;
        }
        LazyLoadContext.ensureOpen(context);
        loaded = context.loadById(targetType, foreignKey);
        return loaded;
    }

    /** Returns the attached entity without a database round-trip (write graph or already loaded). */
    public T attachedEntity() {
        return loaded;
    }

    public boolean isLoaded() {
        return loaded != null;
    }

    public boolean isSet() {
        return foreignKey != null || loaded != null;
    }

    public Object foreignKey() {
        return foreignKey;
    }

    public Class<T> targetType() {
        return targetType;
    }
}
