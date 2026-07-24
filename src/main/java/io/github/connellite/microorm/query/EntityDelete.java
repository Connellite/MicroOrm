package io.github.connellite.microorm.query;

import java.util.Objects;

/** Fluent SQL-oriented DELETE builder for one mapped entity table. */
public final class EntityDelete<T> {

    private final Class<T> entityType;
    private Criterion criterion;
    private boolean allRows;

    private EntityDelete(Class<T> entityType) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    /** Starts a DELETE for the given entity type. */
    public static <T> EntityDelete<T> from(Class<T> entityType) {
        return new EntityDelete<>(entityType);
    }

    public Class<T> entityType() {
        return entityType;
    }

    public Criterion criterion() {
        return criterion;
    }

    public boolean isAllRows() {
        return allRows;
    }

    public EntityDelete<T> where(Criterion criterion) {
        this.criterion = Objects.requireNonNull(criterion, "criterion");
        this.allRows = false;
        return this;
    }

    public EntityDelete<T> and(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.and(criterion);
        this.allRows = false;
        return this;
    }

    public EntityDelete<T> or(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.or(criterion);
        this.allRows = false;
        return this;
    }

    /** Explicitly marks this DELETE as intentionally affecting all rows. */
    public EntityDelete<T> allRows() {
        this.criterion = null;
        this.allRows = true;
        return this;
    }
}
