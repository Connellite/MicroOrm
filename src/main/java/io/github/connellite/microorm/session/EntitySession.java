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
 * Materializing {@code selectRows}/{@code selectOne}/{@code findOne} methods are defaults over {@link #streamRows}.
 */
public interface EntitySession {

    int DEFAULT_INSERT_BATCH_SIZE = 200;

    void createEntity(Class<?> entityClass) throws SQLException;

    void syncEntity(Class<?> entityClass) throws SQLException;

    void dropEntity(Class<?> entityClass) throws SQLException;

    default void updateEntity(Class<?> entityClass) throws SQLException {
        syncEntity(entityClass);
    }

    <T> T insertRow(T entity);

    <T> int insertRows(List<T> entities, int batchSize);

    default <T> int insertRows(List<T> entities) {
        return insertRows(entities, DEFAULT_INSERT_BATCH_SIZE);
    }

    int updateRow(Object entity);

    int deleteRow(Object entity);

    int deleteById(Class<?> entityClass, Object id);

    int deleteAllRows(Class<?> entityClass);

    int execute(EntityInsert<?> insert);

    int execute(EntityUpdate<?> update);

    int execute(EntityDelete<?> delete);

    boolean existsById(Class<?> type, Object id);

    <T> T selectRow(Class<T> type, Object id);

    default <T> Optional<T> findById(Class<T> type, Object id) {
        return Optional.ofNullable(selectRow(type, id));
    }

    <T> Stream<T> streamRows(Class<T> type);

    <T> Stream<T> streamRows(Class<T> type, Map<String, ?> filters);

    <T> Stream<T> streamRows(EntitySelect<T> query);

    <T> Stream<T> streamRows(Class<T> type, Query query);

    default <T> List<T> selectRows(Class<T> type) {
        try (Stream<T> rows = streamRows(type)) {
            return rows.toList();
        }
    }

    default <T> List<T> selectRows(Class<T> type, Map<String, ?> filters) {
        try (Stream<T> rows = streamRows(type, filters)) {
            return rows.toList();
        }
    }

    default <T> List<T> selectRows(EntitySelect<T> query) {
        try (Stream<T> rows = streamRows(query)) {
            return rows.toList();
        }
    }

    default <T> List<T> selectRows(Class<T> type, Query query) {
        try (Stream<T> rows = streamRows(type, query)) {
            return rows.toList();
        }
    }

    default <T> T selectOne(EntitySelect<T> query) {
        return singleResult(collectAtMostTwo(streamRows(query)), true);
    }

    default <T> Optional<T> findOne(EntitySelect<T> query) {
        return Optional.ofNullable(singleResult(collectAtMostTwo(streamRows(query)), false));
    }

    default <T> T selectOne(Class<T> type, Query query) {
        return singleResult(collectAtMostTwo(streamRows(type, query)), true);
    }

    default <T> Optional<T> findOne(Class<T> type, Query query) {
        return Optional.ofNullable(singleResult(collectAtMostTwo(streamRows(type, query)), false));
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
