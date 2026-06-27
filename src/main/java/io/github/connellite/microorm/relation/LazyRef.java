package io.github.connellite.microorm.relation;

import io.github.connellite.reflection.MethodHandleReflectionUtil;
import io.github.connellite.microorm.mapping.ManyToOneField;

import java.util.Objects;

/**
 * Lazy many-to-one reference to a related entity. The target row is loaded on first {@link #get()}
 * while the owning {@link io.github.connellite.microorm.session.Session} is open.
 * <p>
 * For writes use {@link #to(Object)} to reference a managed or new entity, or {@link #toId(Class, Object)}
 * to reference an existing row by primary key without loading it.
 */
public final class LazyRef<T> implements EntityRef<T> {

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

    /**
     * Creates a lazy reference from a join-column value read from JDBC (numeric id, UUID, etc.).
     * Pass {@code null} for a nullable {@code NULL} foreign key; {@link #get()} then returns {@code null}
     * without a database round-trip.
     */
    public static <T> LazyRef<T> of(LazyLoadContext context, Class<T> targetType, Object foreignKey) {
        Objects.requireNonNull(targetType, "targetType");
        return new LazyRef<>(context, targetType, foreignKey, null);
    }

    /**
     * Creates a reference to an entity instance for insert/update (the join column is taken from its primary key
     * when the graph is persisted). Does not load from the database.
     */
    public static <T> LazyRef<T> to(T entity) {
        Objects.requireNonNull(entity, "entity");
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) entity.getClass();
        return new LazyRef<>(null, type, null, entity);
    }

    /**
     * Creates a reference to an existing row by primary key without loading it (write or read-by-id only).
     * Use {@link #foreignKey()} to read the raw id without calling {@link #get()}.
     */
    public static <T> LazyRef<T> toId(Class<T> targetType, Object id) {
        Objects.requireNonNull(targetType, "targetType");
        return new LazyRef<>(null, targetType, id, null);
    }

    /** Sets a {@link LazyRef} on an entity field (VarHandle helper for mapped {@code LazyRef} fields). */
    public static <T> void set(ManyToOneField field, Object owner, LazyRef<T> value) {
        MethodHandleReflectionUtil.set(field.varHandle(), field.javaField(), owner, value);
    }

    /** Reads a {@link LazyRef} from an entity field (VarHandle helper for mapped {@code LazyRef} fields). */
    @SuppressWarnings("unchecked")
    public static <T> LazyRef<T> get(ManyToOneField field, Object owner) {
        return (LazyRef<T>) MethodHandleReflectionUtil.get(field.varHandle(), owner);
    }

    /**
     * Returns the related entity, loading it on first access when this reference was created with
     * {@link #of(LazyLoadContext, Class, Object)} or {@link #toId(Class, Object)}.
     * Returns {@code null} when {@link #isNull()} without querying the database.
     */
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

    /**
     * Returns the entity already held in memory (attached for persist or loaded by a prior {@link #get()}).
     * Never loads from the database. {@code null} if only a foreign key is stored.
     */
    public T attachedEntity() {
        return loaded;
    }

    /** {@code true} after {@link #get()} or {@link #to(Object)} has populated the in-memory entity. */
    public boolean isLoaded() {
        return loaded != null;
    }

    /**
     * {@code true} when this reference points at something: a join-column value, an attached entity,
     * or a row loaded by {@link #get()}. {@code false} when {@link #isNull()}.
     */
    public boolean isSet() {
        return foreignKey != null || loaded != null;
    }

    /**
     * {@code true} when the join column is unset / SQL {@code NULL}: no foreign key and no attached entity.
     * Does not load from the database. Equivalent to {@code !}{@link #isSet()}.
     */
    public boolean isNull() {
        return !isSet();
    }

    /**
     * Raw join-column value (numeric id, UUID, etc.) when known without loading the target row.
     * {@code null} for SQL {@code NULL} or when only {@link #to(Object)} was used — use
     * {@link #attachedEntity()} or {@link io.github.connellite.microorm.mapping.RelationValues#resolveRawForeignKey}
     * for the primary key in that case.
     */
    public Object foreignKey() {
        return foreignKey;
    }

    /** Target entity class declared on the owning {@link io.github.connellite.microorm.annotation.ManyToOne} field. */
    public Class<T> targetType() {
        return targetType;
    }
}
