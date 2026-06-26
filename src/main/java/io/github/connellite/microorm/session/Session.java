package io.github.connellite.microorm.session;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.jdbc.EntityHydrator;
import io.github.connellite.microorm.jdbc.SqlExecutor;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.OneToManyField;
import io.github.connellite.microorm.mapping.RelationPersister;
import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.AbstractSqlGenerator;
import io.github.connellite.microorm.connection.ConnectionProvider;
import io.github.connellite.microorm.connection.SpringJdbcSupport;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Unit of work over a single JDBC {@link Connection}. Not thread-safe — use one session per thread.
 * <p>
 * DML ({@code insert}, {@code update}, {@code delete}, {@code select}) throws {@link MicroOrmException}
 * on failure; DDL ({@code createEntity}, {@code syncEntity}, {@code dropEntity}) declares {@link SQLException}.
 */
public final class Session implements AutoCloseable, RelationPersistSession {

    private final Connection connection;
    private final ConnectionProvider provider;
    private final EntityModelRegistry registry;
    private final SqlGenerator sql;
    private final Dialect dialect;
    private SessionLazyContext lazyContext;

    /** Internal constructor — use {@link io.github.connellite.microorm.MicroOrm#openSession()}. */
    public Session(
            Connection connection,
            ConnectionProvider provider,
            EntityModelRegistry registry,
            Dialect dialect) {
        this.connection = connection;
        this.provider = provider;
        this.registry = registry;
        this.dialect = dialect;
        this.sql = dialect.sqlGenerator();
    }

    /** JDBC connection used by this session (same instance for the session lifetime). */
    public Connection connection() {
        return connection;
    }

    /** Disables auto-commit for explicit {@link #commitTransaction()} / {@link #rollbackTransaction()}. */
    public void beginTransaction() throws SQLException {
        connection.setAutoCommit(false);
    }

    /** Commits the current transaction and re-enables auto-commit. */
    public void commitTransaction() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
        connection.setAutoCommit(true);
    }

    /** Rolls back the current transaction and re-enables auto-commit. */
    public void rollbackTransaction() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
        }
        connection.setAutoCommit(true);
    }

    /**
     * Ensures the entity table exists (creates it when missing). Safe to call on every application
     * startup: never drops an existing table or deletes rows; only creates missing tables and indexes.
     */
    public void createEntity(Class<?> entityClass) throws SQLException {
        EntityModel m = registry.register(entityClass);
        dialect.createTable(connection, m);
    }

    /**
     * Aligns the database schema with the entity mapping (adds missing nullable columns and indexes).
     * Safe to call on every application startup: never drops tables or deletes rows.
     */
    public void syncEntity(Class<?> entityClass) throws SQLException {
        EntityModel m = registry.register(entityClass);
        dialect.syncTable(connection, m);
    }

    /**
     * Alias for {@link #syncEntity(Class)} — brings the schema up to date without data loss.
     */
    public void updateEntity(Class<?> entityClass) throws SQLException {
        syncEntity(entityClass);
    }

    /** Drops the entity table and all rows (destructive). */
    public void dropEntity(Class<?> entityClass) throws SQLException {
        EntityModel m = registry.register(entityClass);
        dialect.dropTable(connection, m);
    }

    /**
     * Inserts one entity row. When the entity has {@link io.github.connellite.microorm.relation.LazyRef} /
     * {@link io.github.connellite.microorm.relation.LazyCollection} fields, persists the object graph in dependency order.
     * Auto-increment and UUID primary keys are filled on the entity when applicable.
     */
    public <T> T insertRow(T entity) {
        Objects.requireNonNull(entity, "insertRow entity cannot be null");
        EntityModel m = registry.get(entity.getClass());
        if (m.hasRelations()) {
            return RelationPersister.insert(this, entity);
        }
        assignGeneratedUuidIfNeeded(entity, m);
        BoundStatement bs = sql.insert(m, entity);
        SqlExecutor.executeInsertReturning(connection, bs, m, entity);
        return entity;
    }

    /**
     * Batch insert of entities of the same class. Uses JDBC batching when the dialect supports it.
     * Entities with relations fall back to sequential {@link #insertRow(Object)} calls.
     *
     * @param batchSize JDBC batch chunk size (values {@code <= 0} use an internal default)
     * @return number of rows inserted
     */
    public <T> int insertRows(List<T> entities, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        Class<?> entityClass = entities.get(0).getClass();
        EntityModel m = registry.get(entityClass);
        if (m.hasRelations()) {
            int total = 0;
            for (T entity : entities) {
                insertRow(entity);
                total++;
            }
            return total;
        }
        boolean omitPk = m.primaryKey().autoIncrement() && EntityHydrator.isUnsetPk(entities.get(0), m.primaryKey());
        List<Map<String, Object>> rows = new java.util.ArrayList<>(entities.size());
        for (T entity : entities) {
            if (entity == null) {
                throw new IllegalArgumentException("Batch entity cannot be null");
            }
            if (entity.getClass() != entityClass) {
                throw new IllegalArgumentException("Batch insert expects entities of the same class");
            }
            boolean entityOmitPk = m.primaryKey().autoIncrement() && EntityHydrator.isUnsetPk(entity, m.primaryKey());
            if (entityOmitPk != omitPk) {
                throw new IllegalArgumentException("Batch insert cannot mix generated and explicit primary keys");
            }
            assignGeneratedUuidIfNeeded(entity, m);
            rows.add(sql.insertParameters(m, entity, omitPk));
        }
        return SqlExecutor.executeBatchInsertReturning(
                connection,
                sql.insertSql(m, omitPk),
                rows,
                batchSize,
                m,
                entities);
    }

    /** Batch insert with default batch size ({@code 200}). */
    @SuppressWarnings("UnusedReturnValue")
    public <T> int insertRows(List<T> entities) {
        return insertRows(entities, 200);
    }

    /** Updates one row by primary key. Returns {@code 0} when no row matched. */
    public int updateRow(Object entity) {
        Objects.requireNonNull(entity, "updateRow entity cannot be null");
        EntityModel m = registry.get(entity.getClass());
        EntityHydrator.requirePkSet(entity, m.primaryKey());
        if (m.hasRelations()) {
            return RelationPersister.update(this, entity);
        }
        return SqlExecutor.executeUpdate(connection, sql.update(m, entity));
    }

    /** Deletes one row by primary key on the entity. Returns {@code 0} when no row matched. */
    public int deleteRow(Object entity) {
        Objects.requireNonNull(entity, "deleteRow entity cannot be null");
        EntityModel m = registry.get(entity.getClass());
        if (m.hasRelations()) {
            return RelationPersister.delete(this, entity);
        }
        EntityHydrator.requirePkSet(entity, m.primaryKey());
        return SqlExecutor.executeUpdate(connection, sql.delete(m, entity));
    }

    /** Deletes one row by primary key value. Returns {@code 0} when no row matched. */
    public int deleteById(Class<?> entityClass, Object id) {
        EntityModel m = registry.get(entityClass);
        EntityHydrator.requirePkValue(id, m.primaryKey());
        return SqlExecutor.executeUpdate(connection, sql.deleteById(m, id));
    }

    /** Deletes all rows from the entity table (table definition is kept). */
    public int deleteAllRows(Class<?> entityClass) {
        EntityModel m = registry.get(entityClass);
        String q = "DELETE FROM " + dialect.sqlName(m.tableIdentifier());
        return SqlExecutor.executeUpdate(connection, BoundStatement.of(q, java.util.Map.of()));
    }

    /** Returns whether a row exists for the given primary key. */
    public boolean existsById(Class<?> type, Object id) {
        EntityModel m = registry.get(type);
        EntityHydrator.requirePkValue(id, m.primaryKey());
        return SqlExecutor.queryExists(connection, sql.existsById(m, id));
    }

    /** Returns {@code null} when no row matches the primary key. */
    public <T> T selectRow(Class<T> type, Object id) {
        return selectRow(type, id, lazyLoadContext());
    }

    <T> T selectRow(Class<T> type, Object id, SessionLazyContext context) {
        EntityModel m = registry.get(type);
        EntityHydrator.requirePkValue(id, m.primaryKey());
        try (Stream<T> rows = SqlExecutor.queryEntitiesStream(
                connection, sql.selectById(m, id), m, dialect, dialect.valueMapper(), context, registry)) {
            List<T> list = rows.toList();
            if (list.isEmpty()) {
                return null;
            }
            if (list.size() > 1) {
                throw new MicroOrmException("Expected at most one row, got " + list.size());
            }
            return list.get(0);
        }
    }

    /** Materializes all rows; closes the underlying JDBC resources. */
    public <T> List<T> selectRows(Class<T> type) {
        try (Stream<T> rows = streamRows(type)) {
            return rows.toList();
        }
    }

    /**
     * Lazy row stream; must be closed (try-with-resources) to release JDBC resources.
     * Hydrated entities receive a session-scoped {@link io.github.connellite.microorm.relation.LazyLoadContext}
     * so {@link io.github.connellite.microorm.relation.LazyRef#get()} and
     * {@link io.github.connellite.microorm.relation.LazyCollection#get()} work until {@link #close()}.
     * Prefer {@link #selectRows(Class)} when the full result fits in memory.
     */
    public <T> Stream<T> streamRows(Class<T> type) {
        EntityModel m = registry.get(type);
        SessionLazyContext context = lazyLoadContext();
        return SqlExecutor.queryEntitiesStream(
                connection, sql.selectAll(m), m, dialect, dialect.valueMapper(), context, registry);
    }

    /** Materializes filtered rows; closes the underlying JDBC resources. */
    public <T> List<T> selectRows(Class<T> type, Map<String, ?> filters) {
        try (Stream<T> rows = streamRows(type, filters)) {
            return rows.toList();
        }
    }

    /** Lazy filtered row stream; must be closed (try-with-resources). Supports lazy associations like {@link #streamRows(Class)}. */
    public <T> Stream<T> streamRows(Class<T> type, Map<String, ?> filters) {
        EntityModel m = registry.get(type);
        SessionLazyContext context = lazyLoadContext();
        return SqlExecutor.queryEntitiesStream(
                connection, sql.selectWhere(m, filters), m, dialect, dialect.valueMapper(), context, registry);
    }

    /** Materializes custom-query rows; closes the underlying JDBC resources. */
    public <T> List<T> selectRows(Class<T> type, Query query) {
        try (Stream<T> rows = streamRows(type, query)) {
            return rows.toList();
        }
    }

    /** Lazy custom-query row stream; must be closed (try-with-resources). Supports lazy associations like {@link #streamRows(Class)}. */
    public <T> Stream<T> streamRows(Class<T> type, Query query) {
        EntityModel m = registry.get(type);
        SessionLazyContext context = lazyLoadContext();
        return SqlExecutor.queryEntitiesStream(
                connection, query, m, dialect, dialect.valueMapper(), context, registry);
    }

    /**
     * Runs a custom update/delete {@link Query} (for example bulk updates not covered by entity CRUD).
     *
     * @return affected row count
     */
    public int execute(Query query) {
        Objects.requireNonNull(query, "query cannot be null");
        return SqlExecutor.executeUpdate(connection, query);
    }

    private SessionLazyContext lazyLoadContext() {
        if (lazyContext == null) {
            lazyContext = new SessionLazyContext(this, connection, registry, dialect);
        }
        return lazyContext;
    }

    @Override
    public void assignGeneratedUuidIfNeeded(Object entity, EntityModel model) {
        if (model.primaryKey().autoIncrement()) {
            return;
        }
        if (model.primaryKey().javaType() == UUID.class && EntityHydrator.getFieldValue(entity, model.primaryKey()) == null) {
            EntityHydrator.setFieldValue(entity, model.primaryKey(), UUID.randomUUID());
        }
    }

    @Override
    public EntityModelRegistry registry() {
        return registry;
    }

    @Override
    public void requirePkSet(Object entity, EntityModel model) {
        EntityHydrator.requirePkSet(entity, model.primaryKey());
    }

    @Override
    public Object pkValue(Object entity, EntityModel model) {
        return EntityHydrator.getFieldValue(entity, model.primaryKey());
    }

    @Override
    public void insertEntityRow(Object entity, EntityModel model, List<RelationPersister.DeferredFkUpdate> deferred) {
        EntityField pk = model.primaryKey();
        boolean omitPk = pk.autoIncrement() && EntityHydrator.isUnsetPk(entity, pk);
        AbstractSqlGenerator generator = (AbstractSqlGenerator) sql;
        AbstractSqlGenerator.RelationInsertParts parts =
                generator.buildRelationInsert(model, entity, omitPk, registry, deferred);
        BoundStatement bs = BoundStatement.of(parts.sql(), parts.parameters());
        SqlExecutor.executeInsertReturning(connection, bs, model, entity);
    }

    @Override
    public int updateEntityRow(Object entity, EntityModel model) {
        return updateEntityRow(entity, model, List.of());
    }

    @Override
    public int updateEntityRow(Object entity, EntityModel model, List<RelationPersister.DeferredFkUpdate> deferred) {
        EntityHydrator.requirePkSet(entity, model.primaryKey());
        AbstractSqlGenerator generator = (AbstractSqlGenerator) sql;
        return SqlExecutor.executeUpdate(connection, generator.update(model, entity, registry, deferred));
    }

    @Override
    public void updateJoinColumn(Object entity, EntityModel model, ManyToOneField relation) {
        AbstractSqlGenerator generator = (AbstractSqlGenerator) sql;
        SqlExecutor.executeUpdate(connection, generator.updateJoinColumn(model, entity, relation, registry));
    }

    @Override
    public int deleteEntityRow(Object entity, EntityModel model) {
        EntityHydrator.requirePkSet(entity, model.primaryKey());
        return SqlExecutor.executeUpdate(connection, sql.delete(model, entity));
    }

    @Override
    public void deleteChildrenByOwner(OneToManyField relation, Object ownerPk) {
        EntityModel childModel = registry.get(relation.targetEntityClass());
        ManyToOneField inverse = childModel.manyToOneByFieldName(relation.mappedBy());
        EntityModel ownerModel = registry.get(inverse.targetEntityClass());
        Object jdbcOwnerPk = dialect.valueMapper().toJdbcValue(ownerModel.primaryKey(), ownerPk);
        execute(Query.of("DELETE FROM " + dialect.sqlName(childModel.tableIdentifier())
                        + " WHERE " + dialect.sqlName(inverse.joinColumnIdentifier()) + " = :ownerId")
                .set("ownerId", jdbcOwnerPk));
    }

    @Override
    public void deleteOrphanChildren(
            OneToManyField relation,
            Object ownerPk,
            Set<Object> retainedChildPks,
            EntityModel childModel) {
        ManyToOneField inverse = childModel.manyToOneByFieldName(relation.mappedBy());
        EntityModel ownerModel = registry.get(inverse.targetEntityClass());
        Object jdbcOwnerPk = dialect.valueMapper().toJdbcValue(ownerModel.primaryKey(), ownerPk);
        try (Stream<?> rows = SqlExecutor.queryEntitiesStream(
                connection,
                sql.selectByJoinColumn(childModel, inverse.joinColumn(), jdbcOwnerPk),
                childModel,
                dialect,
                dialect.valueMapper(),
                lazyLoadContext(),
                registry)) {
            rows.forEach(existing -> {
                Object childPk = EntityHydrator.getFieldValue(existing, childModel.primaryKey());
                if (!retainedChildPks.contains(childPk)) {
                    deleteEntityRow(existing, childModel);
                }
            });
        }
    }

    /**
     * Releases the JDBC connection. Rolls back an open local transaction unless the connection is
     * managed by Spring ({@code TransactionAwareDataSourceProxy}), in which case commit/rollback is
     * left to Spring.
     */
    @Override
    public void close() throws SQLException {
        if (lazyContext != null) {
            lazyContext.markClosed();
        }
        if (connection != null && !connection.isClosed()) {
            if (!connection.getAutoCommit() && !SpringJdbcSupport.isTransactionManagedConnection(connection)) {
                connection.rollback();
            }
            provider.release(connection);
        }
    }
}
