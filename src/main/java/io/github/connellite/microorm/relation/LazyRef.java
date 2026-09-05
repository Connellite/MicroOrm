package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.OneToOneField;

import java.util.Objects;

/**
 * Lazy to-one reference to a related entity. The target row is loaded on first {@link #get()}
 * while the owning {@link io.github.connellite.microorm.session.Session} is open.
 * <p>
 * For writes use {@link #to(Object)} to reference a managed or new entity, or {@link #toId(Class, Object)}
 * to reference an existing row by primary key without loading it.
 */
public final class LazyRef<T> extends EntityRef<T> {

    private final LazyLoadContext context;
    private final OneToOneField inverseRelation;

    private LazyRef(LazyLoadContext context, Class<T> targetType, Object foreignKey, T loaded) {
        this(context, targetType, foreignKey, loaded, null);
    }

    private LazyRef(
            LazyLoadContext context,
            Class<T> targetType,
            Object foreignKey,
            T loaded,
            OneToOneField inverseRelation) {
        super(targetType, foreignKey, loaded);
        this.context = context;
        this.inverseRelation = inverseRelation;
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

    /**
     * Creates a lazy inverse {@code @OneToOne} that loads the related row by the owning join column
     * on first {@link #get()}.
     */
    @SuppressWarnings("unchecked")
    public static <T> LazyRef<T> ofInverse(LazyLoadContext context, OneToOneField relation, Object ownerId) {
        Objects.requireNonNull(relation, "relation");
        return new LazyRef<>(context, (Class<T>) relation.targetEntityClass(), ownerId, null, relation);
    }

    /** Sets a {@link LazyRef} on an entity field (VarHandle helper for mapped {@code LazyRef} fields). */
    public static <T> void set(ManyToOneField field, Object owner, LazyRef<T> value) {
        EntityRef.set(field, owner, value);
    }

    /** Sets a {@link LazyRef} on a mapped {@code @OneToOne} field. */
    public static <T> void set(OneToOneField field, Object owner, LazyRef<T> value) {
        EntityRef.set(field, owner, value);
    }

    /** Reads a {@link LazyRef} from an entity field (VarHandle helper for mapped {@code LazyRef} fields). */
    @SuppressWarnings("unchecked")
    public static <T> LazyRef<T> get(ManyToOneField field, Object owner) {
        return (LazyRef<T>) EntityRef.get(field, owner);
    }

    /** Reads a {@link LazyRef} from a mapped {@code @OneToOne} field. */
    @SuppressWarnings("unchecked")
    public static <T> LazyRef<T> get(OneToOneField field, Object owner) {
        return (LazyRef<T>) EntityRef.get(field, owner);
    }

    /**
     * Returns the related entity, loading it on first access when this reference was created with
     * {@link #of(LazyLoadContext, Class, Object)} or {@link #toId(Class, Object)}.
     * Returns {@code null} when {@link #isNull()} without querying the database.
     */
    @Override
    public T get() {
        T attached = attachedEntity();
        if (attached != null) {
            return attached;
        }
        if (inverseRelation != null) {
            if (foreignKey() == null) {
                return null;
            }
            LazyLoadContext.ensureOpen(context);
            attach(context.loadInverseOneToOne(inverseRelation, foreignKey()));
            return attachedEntity();
        }
        if (foreignKey() == null) {
            return null;
        }
        LazyLoadContext.ensureOpen(context);
        attach(context.loadById(targetType(), foreignKey()));
        return attachedEntity();
    }
}
