package io.github.connellite.microorm.repository;

import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.sql.Query;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    T insertRow(T entity);

    /** Batch insert using the session default batch size. */
    int insertRows(List<T> entities);

    /** Batch insert using the supplied batch size. */
    int insertRows(List<T> entities, int batchSize);

    /** Updates one entity row by primary key. */
    int updateRow(T entity);

    /** Deletes one entity row by primary key. */
    int deleteRow(T entity);

    /** Deletes one entity row by primary-key value. */
    int deleteById(ID id);

    /** Deletes all rows from the entity table. */
    int deleteAllRows();

    /** Returns whether a row exists for the primary-key value. */
    boolean existsById(ID id);

    /** Returns {@code null} when no row matches the primary-key value. */
    T selectRow(ID id);

    /** Returns an optional row by primary-key value. */
    Optional<T> findById(ID id);

    /** Returns all rows. */
    List<T> selectRows();

    /** Returns rows matching simple field filters. */
    List<T> selectRows(Map<String, ?> filters);

    /** Returns rows matching an entity query. */
    List<T> selectRows(EntityQuery<T> query);

    /** Returns exactly one row matching an entity query. */
    T selectOne(EntityQuery<T> query);

    /** Returns zero or one row matching an entity query. */
    Optional<T> findOne(EntityQuery<T> query);

    /** Returns rows from a custom SQL query mapped to the entity type. */
    List<T> selectRows(Query query);

    /** Returns exactly one row from a custom SQL query. */
    T selectOne(Query query);

    /** Returns zero or one row from a custom SQL query. */
    Optional<T> findOne(Query query);
}
