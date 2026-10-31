package io.github.connellite.microorm.repository;

import io.github.connellite.microorm.query.EntitySelect;
import io.github.connellite.microorm.session.EntitySession;
import io.github.connellite.microorm.sql.Query;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Typed facade over entity methods already provided by {@link io.github.connellite.microorm.session.Session}.
 *
 * @param <T> entity type managed by this repository
 * @param <ID> primary-key type
 */
public interface EntityRepository<T, ID> {

    /** Ensures the entity table exists. */
    void createEntity() throws SQLException;

    /** Synchronizes the entity table with nullable mapped columns and indexes. */
    void syncEntity() throws SQLException;

    /** Alias for {@link #syncEntity()}. */
    void updateEntity() throws SQLException;

    /** Drops the entity table. */
    void dropEntity() throws SQLException;

    /** Inserts one entity row. */
    T insert(T entity);

    /** Batch insert using the supplied batch size. */
    int insertAll(List<T> entities, int batchSize);

    /** Batch insert using the session default batch size. */
    default int insertAll(List<T> entities) {
        return insertAll(entities, EntitySession.DEFAULT_INSERT_BATCH_SIZE);
    }

    /** Updates one entity row by primary key. */
    int update(T entity);

    /** Deletes one entity row by primary key. */
    int delete(T entity);

    /** Deletes one entity row by primary-key value. */
    int deleteById(ID id);

    /** Deletes all rows from the entity table. */
    int deleteAll();

    /** Returns whether a row exists for the primary-key value. */
    boolean existsById(ID id);

    /** Returns {@code null} when no row matches the primary-key value. */
    T select(ID id);

    /** Returns an optional row by primary-key value. */
    Optional<T> findById(ID id);

    /** Returns all rows. */
    default List<T> selectAll() {
        try (Stream<T> rows = stream()) {
            return rows.toList();
        }
    }

    /**
     * Lazy row stream; must be closed (try-with-resources) to release JDBC resources.
     * Prefer {@link #selectAll()} when the full result fits in memory.
     */
    Stream<T> stream();

    /** Returns rows matching simple field filters. */
    default List<T> select(Map<String, ?> filters) {
        try (Stream<T> rows = stream(filters)) {
            return rows.toList();
        }
    }

    /**
     * Lazy filtered row stream; must be closed (try-with-resources).
     */
    Stream<T> stream(Map<String, ?> filters);

    /** Returns rows matching an entity query. */
    default List<T> select(EntitySelect<T> query) {
        try (Stream<T> rows = stream(query)) {
            return rows.toList();
        }
    }

    /**
     * Lazy entity-query stream; must be closed (try-with-resources).
     */
    Stream<T> stream(EntitySelect<T> query);

    /** Returns exactly one row matching an entity query. */
    T selectOne(EntitySelect<T> query);

    /** Returns zero or one row matching an entity query. */
    Optional<T> findOne(EntitySelect<T> query);

    /** Returns rows from a custom SQL query mapped to the entity type. */
    default List<T> select(Query query) {
        try (Stream<T> rows = stream(query)) {
            return rows.toList();
        }
    }

    /**
     * Lazy custom-query row stream; must be closed (try-with-resources).
     */
    Stream<T> stream(Query query);

    /** Returns exactly one row from a custom SQL query. */
    T selectOne(Query query);

    /** Returns zero or one row from a custom SQL query. */
    Optional<T> findOne(Query query);

    /**
     * Drops every cached instance and known id in the bound session.
     * Useful when the repository comes from {@link io.github.connellite.microorm.session.Session#repository(Class)}.
     */
    void clear();

    /** Drops this instance. The next read of its id goes to the database. */
    void evict(T entity);

    /** Drops the cached instance or known id of this entity type. */
    void evictById(ID id);

    /** Drops every cached instance and known id of this entity type. Other types stay. */
    void evictAll();

    /** Number of entity instances held in the bound session context. Known ids without an instance are not counted. */
    int cachedCount();

    /** @deprecated Use {@link #insert(Object)}. */
    @Deprecated(forRemoval = true)
    default T insertRow(T entity) {
        return insert(entity);
    }

    /** @deprecated Use {@link #insertAll(List, int)}. */
    @Deprecated(forRemoval = true)
    default int insertRows(List<T> entities, int batchSize) {
        return insertAll(entities, batchSize);
    }

    /** @deprecated Use {@link #insertAll(List)}. */
    @Deprecated(forRemoval = true)
    default int insertRows(List<T> entities) {
        return insertAll(entities);
    }

    /** @deprecated Use {@link #update(Object)}. */
    @Deprecated(forRemoval = true)
    default int updateRow(T entity) {
        return update(entity);
    }

    /** @deprecated Use {@link #delete(Object)}. */
    @Deprecated(forRemoval = true)
    default int deleteRow(T entity) {
        return delete(entity);
    }

    /** @deprecated Use {@link #deleteAll()}. */
    @Deprecated(forRemoval = true)
    default int deleteAllRows() {
        return deleteAll();
    }

    /** @deprecated Use {@link #select(Object)}. */
    @Deprecated(forRemoval = true)
    default T selectRow(ID id) {
        return select(id);
    }

    /** @deprecated Use {@link #selectAll()}. */
    @Deprecated(forRemoval = true)
    default List<T> selectRows() {
        return selectAll();
    }

    /** @deprecated Use {@link #stream()}. */
    @Deprecated(forRemoval = true)
    default Stream<T> streamRows() {
        return stream();
    }

    /** @deprecated Use {@link #select(Map)}. */
    @Deprecated(forRemoval = true)
    default List<T> selectRows(Map<String, ?> filters) {
        return select(filters);
    }

    /** @deprecated Use {@link #stream(Map)}. */
    @Deprecated(forRemoval = true)
    default Stream<T> streamRows(Map<String, ?> filters) {
        return stream(filters);
    }

    /** @deprecated Use {@link #select(EntitySelect)}. */
    @Deprecated(forRemoval = true)
    default List<T> selectRows(EntitySelect<T> query) {
        return select(query);
    }

    /** @deprecated Use {@link #stream(EntitySelect)}. */
    @Deprecated(forRemoval = true)
    default Stream<T> streamRows(EntitySelect<T> query) {
        return stream(query);
    }

    /** @deprecated Use {@link #select(Query)}. */
    @Deprecated(forRemoval = true)
    default List<T> selectRows(Query query) {
        return select(query);
    }

    /** @deprecated Use {@link #stream(Query)}. */
    @Deprecated(forRemoval = true)
    default Stream<T> streamRows(Query query) {
        return stream(query);
    }
}
