package io.github.connellite.microorm.session;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.query.EntityDelete;
import io.github.connellite.microorm.query.EntityInsert;
import io.github.connellite.microorm.query.EntitySelect;
import io.github.connellite.microorm.query.EntityUpdate;
import io.github.connellite.microorm.sql.Query;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Entity CRUD, typed mutation DSL, and query surface implemented by {@link Session}.
 * Materializing {@code select}/{@code selectAll}/{@code selectOne}/{@code findOne} methods are defaults over {@link #stream(Class)}.
 */
public interface EntitySession {

    int DEFAULT_INSERT_BATCH_SIZE = 200;

    void createEntity(Class<?> entityClass) throws SQLException;

    void syncEntity(Class<?> entityClass) throws SQLException;

    void dropEntity(Class<?> entityClass) throws SQLException;

    default void updateEntity(Class<?> entityClass) throws SQLException {
        syncEntity(entityClass);
    }

    <T> T insert(T entity);

    <T> int insertAll(List<T> entities, int batchSize);

    default <T> int insertAll(List<T> entities) {
        return insertAll(entities, DEFAULT_INSERT_BATCH_SIZE);
    }

    int update(Object entity);

    int delete(Object entity);

    int deleteById(Class<?> entityClass, Object id);

    int deleteAll(Class<?> entityClass);

    /**
     * Drops every cached instance and known id. The next read goes to the database.
     */
    void clear();

    /**
     * Drops one cached instance. The next read of that id goes to the database.
     */
    void evict(Object entity);

    /**
     * Drops the cached instance or known id for {@code (entityClass, id)}.
     */
    void evict(Class<?> entityClass, Object id);

    /**
     * Drops every cached instance and known id of {@code entityClass}. Other classes stay.
     */
    void evict(Class<?> entityClass);

    /**
     * Number of entity instances held in the session context. Known ids without an instance are not counted.
     */
    int cachedCount();

    int execute(EntityInsert<?> insert);

    int execute(EntityUpdate<?> update);

    int execute(EntityDelete<?> delete);

    boolean existsById(Class<?> type, Object id);

    <T> T select(Class<T> type, Object id);

    default <T> Optional<T> findById(Class<T> type, Object id) {
        return Optional.ofNullable(select(type, id));
    }

    <T> Stream<T> stream(Class<T> type);

    <T> Stream<T> stream(Class<T> type, Map<String, ?> filters);

    <T> Stream<T> stream(EntitySelect<T> query);

    <T> Stream<T> stream(Class<T> type, Query query);

    default <T> List<T> selectAll(Class<T> type) {
        try (Stream<T> rows = stream(type)) {
            return rows.toList();
        }
    }

    default <T> List<T> select(Class<T> type, Map<String, ?> filters) {
        try (Stream<T> rows = stream(type, filters)) {
            return rows.toList();
        }
    }

    default <T> List<T> select(EntitySelect<T> query) {
        try (Stream<T> rows = stream(query)) {
            return rows.toList();
        }
    }

    default <T> List<T> select(Class<T> type, Query query) {
        try (Stream<T> rows = stream(type, query)) {
            return rows.toList();
        }
    }

    default <T> T selectOne(EntitySelect<T> query) {
        return singleResult(collectAtMostTwo(stream(query)), true);
    }

    default <T> Optional<T> findOne(EntitySelect<T> query) {
        return Optional.ofNullable(singleResult(collectAtMostTwo(stream(query)), false));
    }

    default <T> T selectOne(Class<T> type, Query query) {
        return singleResult(collectAtMostTwo(stream(type, query)), true);
    }

    default <T> Optional<T> findOne(Class<T> type, Query query) {
        return Optional.ofNullable(singleResult(collectAtMostTwo(stream(type, query)), false));
    }

    /** @deprecated Use {@link #insert(Object)}. */
    @Deprecated(forRemoval = true)
    default <T> T insertRow(T entity) {
        return insert(entity);
    }

    /** @deprecated Use {@link #insertAll(List, int)}. */
    @Deprecated(forRemoval = true)
    default <T> int insertRows(List<T> entities, int batchSize) {
        return insertAll(entities, batchSize);
    }

    /** @deprecated Use {@link #insertAll(List)}. */
    @Deprecated(forRemoval = true)
    default <T> int insertRows(List<T> entities) {
        return insertAll(entities);
    }

    /** @deprecated Use {@link #update(Object)}. */
    @Deprecated(forRemoval = true)
    default int updateRow(Object entity) {
        return update(entity);
    }

    /** @deprecated Use {@link #delete(Object)}. */
    @Deprecated(forRemoval = true)
    default int deleteRow(Object entity) {
        return delete(entity);
    }

    /** @deprecated Use {@link #deleteAll(Class)}. */
    @Deprecated(forRemoval = true)
    default int deleteAllRows(Class<?> entityClass) {
        return deleteAll(entityClass);
    }

    /** @deprecated Use {@link #select(Class, Object)}. */
    @Deprecated(forRemoval = true)
    default <T> T selectRow(Class<T> type, Object id) {
        return select(type, id);
    }

    /** @deprecated Use {@link #stream(Class)}. */
    @Deprecated(forRemoval = true)
    default <T> Stream<T> streamRows(Class<T> type) {
        return stream(type);
    }

    /** @deprecated Use {@link #stream(Class, Map)}. */
    @Deprecated(forRemoval = true)
    default <T> Stream<T> streamRows(Class<T> type, Map<String, ?> filters) {
        return stream(type, filters);
    }

    /** @deprecated Use {@link #stream(EntitySelect)}. */
    @Deprecated(forRemoval = true)
    default <T> Stream<T> streamRows(EntitySelect<T> query) {
        return stream(query);
    }

    /** @deprecated Use {@link #stream(Class, Query)}. */
    @Deprecated(forRemoval = true)
    default <T> Stream<T> streamRows(Class<T> type, Query query) {
        return stream(type, query);
    }

    /** @deprecated Use {@link #selectAll(Class)}. */
    @Deprecated(forRemoval = true)
    default <T> List<T> selectRows(Class<T> type) {
        return selectAll(type);
    }

    /** @deprecated Use {@link #select(Class, Map)}. */
    @Deprecated(forRemoval = true)
    default <T> List<T> selectRows(Class<T> type, Map<String, ?> filters) {
        return select(type, filters);
    }

    /** @deprecated Use {@link #select(EntitySelect)}. */
    @Deprecated(forRemoval = true)
    default <T> List<T> selectRows(EntitySelect<T> query) {
        return select(query);
    }

    /** @deprecated Use {@link #select(Class, Query)}. */
    @Deprecated(forRemoval = true)
    default <T> List<T> selectRows(Class<T> type, Query query) {
        return select(type, query);
    }

    private static <T> List<T> collectAtMostTwo(Stream<T> rows) {
        try (rows) {
            return rows.limit(2).toList();
        }
    }

    private static <T> T singleResult(List<T> rows, boolean requireOne) {
        if (rows.size() > 1) {
            throw new MicroOrmException("Expected at most one row, got " + rows.size());
        }
        if (rows.isEmpty()) {
            if (requireOne) {
                throw new MicroOrmException("Expected one row, got 0");
            }
            return null;
        }
        return rows.get(0);
    }
}
