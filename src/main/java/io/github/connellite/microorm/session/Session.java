package io.github.connellite.microorm.session;

import io.github.connellite.collections.NullSkippingArrayList;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.jdbc.EntityHydrator;
import io.github.connellite.microorm.jdbc.SqlExecutor;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.LifecycleCallbacks;
import io.github.connellite.microorm.mapping.LifecycleEvent;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.OneToManyField;
import io.github.connellite.microorm.mapping.RelationPersister;
import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.repository.EntityRepository;
import io.github.connellite.microorm.repository.RepositoryProxyFactory;
import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.RelationSqlGenerator;
import io.github.connellite.microorm.connection.ConnectionProvider;
import io.github.connellite.microorm.connection.SpringJdbcSupport;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Unit of work over a single JDBC {@link Connection}. Not thread-safe — use one session per thread.
 * <p>
 * Provides entity CRUD, schema helpers, map-based filtered selects, {@link EntityQuery} execution,
 * custom {@link Query} reads, and lazy or eager association loading through
 * {@link io.github.connellite.microorm.relation} wrappers.
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
    private final List<TransactionalEventVisitorRegistration<?>> transactionalEventListeners = new ArrayList<>();
    private final List<Object> transactionEvents = new NullSkippingArrayList<>();
    private SessionLazyContext lazyContext;
    private boolean activeStream;
    private boolean localTransactionActive;

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
        if (localTransactionActive) {
            throw new MicroOrmException("Session transaction already active");
        }
        transactionEvents.clear();
        connection.setAutoCommit(false);
        localTransactionActive = true;
    }

    /** Commits the current transaction and re-enables auto-commit. */
    public void commitTransaction() throws SQLException {
        if (!connection.getAutoCommit()) {
            if (localTransactionActive) {
                dispatchBeforeCommit();
            }
            connection.commit();
            try {
                if (localTransactionActive) {
                    dispatchAfterCommit();
                }
            } finally {
                completeLocalTransaction();
            }
        } else {
            completeLocalTransaction();
        }
        connection.setAutoCommit(true);
    }

    /** Rolls back the current transaction and re-enables auto-commit. */
    public void rollbackTransaction() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
            try {
                if (localTransactionActive) {
                    dispatchAfterRollback();
                }
            } finally {
                completeLocalTransaction();
            }
        } else {
            completeLocalTransaction();
        }
        connection.setAutoCommit(true);
    }

    /**
     * Registers a typed listener for events published through {@link #publishEvent(Object)}.
     * <p>
     * Events are delivered only for explicit {@link #beginTransaction()} transactions unless
     * {@code fallbackExecution} is enabled.
     */
    public <E> Session addTransactionalEventListener(
            Class<E> eventType,
            TransactionalEventVisitor<? super E> visitor) {
        return addTransactionalEventListener(eventType, false, visitor);
    }

    /**
     * Registers a typed listener for events published through {@link #publishEvent(Object)}.
     *
     * @param fallbackExecution when {@code true}, events published outside a local transaction are delivered as
     *                          {@code afterCommit} followed by {@code afterCompletion}
     */
    public <E> Session addTransactionalEventListener(
            Class<E> eventType,
            boolean fallbackExecution,
            TransactionalEventVisitor<? super E> visitor) {
        transactionalEventListeners.add(new TransactionalEventVisitorRegistration<>(
                Objects.requireNonNull(eventType, "eventType"),
                fallbackExecution,
                Objects.requireNonNull(visitor, "visitor")));
        return this;
    }

    /**
     * Publishes an event for transaction-phase delivery.
     * <p>
     * Inside a local session transaction, the event is queued until commit or rollback. Outside a local
     * transaction, only listeners registered with {@code fallbackExecution = true} are invoked.
     */
    public void publishEvent(Object event) {
        Objects.requireNonNull(event, "event");
        if (localTransactionActive) {
            transactionEvents.add(event);
        } else {
            dispatchFallbackEvent(event);
        }
    }

    /** Creates a typed repository proxy bound to this session. */
    public <R extends EntityRepository<?, ?>> R repository(Class<R> repositoryType) {
        return RepositoryProxyFactory.create(repositoryType, operation -> operation.apply(this));
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
     * Inserts one entity row. When the entity has relation wrapper fields, persists the object graph in dependency order.
     * Auto-increment and UUID primary keys are filled on the entity when applicable.
     */
    public <T> T insertRow(T entity) {
        Objects.requireNonNull(entity, "insertRow entity cannot be null");
        EntityModel m = registry.get(entity.getClass());
        if (m.hasRelations()) {
            return RelationPersister.insert(this, entity);
        }
        assignGeneratedUuidIfNeeded(entity, m);
        LifecycleCallbacks.invoke(entity, LifecycleEvent.PRE_PERSIST);
        BoundStatement bs = sql.insert(m, entity);
        SqlExecutor.executeInsertReturning(connection, bs, m, entity);
        LifecycleCallbacks.invoke(entity, LifecycleEvent.POST_PERSIST);
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
        List<Map<String, Object>> rows = new ArrayList<>(entities.size());
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
            LifecycleCallbacks.invoke(entity, LifecycleEvent.PRE_PERSIST);
            rows.add(sql.insertParameters(m, entity, omitPk));
        }
        int inserted = SqlExecutor.executeBatchInsertReturning(
                connection,
                sql.insertSql(m, omitPk),
                rows,
                batchSize,
                m,
                entities);
        for (T entity : entities) {
            LifecycleCallbacks.invoke(entity, LifecycleEvent.POST_PERSIST);
        }
        return inserted;
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
        LifecycleCallbacks.invoke(entity, LifecycleEvent.PRE_UPDATE);
        int updated = SqlExecutor.executeUpdate(connection, sql.update(m, entity));
        if (updated > 0) {
            LifecycleCallbacks.invoke(entity, LifecycleEvent.POST_UPDATE);
        }
        return updated;
    }

    /** Deletes one row by primary key on the entity. Returns {@code 0} when no row matched. */
    public int deleteRow(Object entity) {
        Objects.requireNonNull(entity, "deleteRow entity cannot be null");
        EntityModel m = registry.get(entity.getClass());
        if (m.hasRelations()) {
            return RelationPersister.delete(this, entity);
        }
        EntityHydrator.requirePkSet(entity, m.primaryKey());
        LifecycleCallbacks.invoke(entity, LifecycleEvent.PRE_REMOVE);
        int deleted = SqlExecutor.executeUpdate(connection, sql.delete(m, entity));
        if (deleted > 0) {
            LifecycleCallbacks.invoke(entity, LifecycleEvent.POST_REMOVE);
        }
        return deleted;
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
        String q = "DELETE FROM " + m.sqlTableName(dialect);
        return SqlExecutor.executeUpdate(connection, BoundStatement.of(q, Map.of()));
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

    /** Returns an {@link Optional} row by primary key. */
    public <T> Optional<T> findById(Class<T> type, Object id) {
        return Optional.ofNullable(selectRow(type, id));
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
     * so lazy relation wrappers can load related rows until {@link #close()}.
     * Prefer {@link #selectRows(Class)} when the full result fits in memory.
     */
    public <T> Stream<T> streamRows(Class<T> type) {
        return openStream(() -> {
            EntityModel m = registry.get(type);
            SessionLazyContext context = lazyLoadContext();
            return SqlExecutor.queryEntitiesStream(
                    connection, sql.selectAll(m), m, dialect, dialect.valueMapper(), context, registry);
        });
    }

    /** Materializes filtered rows; closes the underlying JDBC resources. */
    public <T> List<T> selectRows(Class<T> type, Map<String, ?> filters) {
        try (Stream<T> rows = streamRows(type, filters)) {
            return rows.toList();
        }
    }

    /** Lazy filtered row stream; must be closed (try-with-resources). Supports lazy associations like {@link #streamRows(Class)}. */
    public <T> Stream<T> streamRows(Class<T> type, Map<String, ?> filters) {
        return openStream(() -> {
            EntityModel m = registry.get(type);
            SessionLazyContext context = lazyLoadContext();
            return SqlExecutor.queryEntitiesStream(
                    connection, sql.selectWhere(m, filters), m, dialect, dialect.valueMapper(), context, registry);
        });
    }

    /** Materializes rows matching an {@link EntityQuery}; closes the underlying JDBC resources. */
    public <T> List<T> selectRows(EntityQuery<T> query) {
        try (Stream<T> rows = streamRows(query)) {
            return rows.toList();
        }
    }

    /** Returns exactly one row matching an {@link EntityQuery}; throws when none or multiple rows match. */
    public <T> T selectOne(EntityQuery<T> query) {
        return singleResult(findAtMostTwo(query), true);
    }

    /** Returns zero or one row matching an {@link EntityQuery}; throws when multiple rows match. */
    public <T> Optional<T> findOne(EntityQuery<T> query) {
        return Optional.ofNullable(singleResult(findAtMostTwo(query), false));
    }

    /**
     * Lazy entity-query stream; must be closed (try-with-resources). Supports lazy associations like {@link #streamRows(Class)}.
     */
    public <T> Stream<T> streamRows(EntityQuery<T> query) {
        Objects.requireNonNull(query, "query cannot be null");
        return openStream(() -> {
            EntityModel m = registry.get(query.entityType());
            SessionLazyContext context = lazyLoadContext();
            return SqlExecutor.queryEntitiesStream(
                    connection, sql.select(m, query, registry), m, dialect, dialect.valueMapper(), context, registry);
        });
    }

    /** Materializes custom-query rows; closes the underlying JDBC resources. */
    public <T> List<T> selectRows(Class<T> type, Query query) {
        try (Stream<T> rows = streamRows(type, query)) {
            return rows.toList();
        }
    }

    /** Returns exactly one row from a custom {@link Query}; throws when none or multiple rows match. */
    public <T> T selectOne(Class<T> type, Query query) {
        return singleResult(findAtMostTwo(type, query), true);
    }

    /** Returns zero or one row from a custom {@link Query}; throws when multiple rows match. */
    public <T> Optional<T> findOne(Class<T> type, Query query) {
        return Optional.ofNullable(singleResult(findAtMostTwo(type, query), false));
    }

    /** Lazy custom-query row stream; must be closed (try-with-resources). Supports lazy associations like {@link #streamRows(Class)}. */
    public <T> Stream<T> streamRows(Class<T> type, Query query) {
        return openStream(() -> {
            EntityModel m = registry.get(type);
            SessionLazyContext context = lazyLoadContext();
            return SqlExecutor.queryEntitiesStream(
                    connection, query, m, dialect, dialect.valueMapper(), context, registry);
        });
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

    private <T> List<T> findAtMostTwo(EntityQuery<T> query) {
        try (Stream<T> rows = streamRows(query)) {
            return rows.limit(2).toList();
        }
    }

    private <T> List<T> findAtMostTwo(Class<T> type, Query query) {
        try (Stream<T> rows = streamRows(type, query)) {
            return rows.limit(2).toList();
        }
    }

    private <T> T singleResult(List<T> rows, boolean requireOne) {
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

    private SessionLazyContext lazyLoadContext() {
        if (lazyContext == null) {
            lazyContext = new SessionLazyContext(this, connection, registry, dialect);
        }
        return lazyContext;
    }

    private <T> Stream<T> openStream(Supplier<Stream<T>> streamFactory) {
        if (activeStream) {
            throw new MicroOrmException("Session already has an active stream; close it before opening another stream");
        }
        activeStream = true;
        try {
            return streamFactory.get().onClose(() -> activeStream = false);
        } catch (RuntimeException e) {
            activeStream = false;
            throw e;
        }
    }

    private void dispatchBeforeCommit() {
        for (Object event : transactionEvents) {
            for (TransactionalEventVisitorRegistration<?> registration : transactionalEventListeners) {
                if (registration.matches(event)) {
                    registration.beforeCommit(event);
                }
            }
        }
    }

    private void dispatchAfterCommit() {
        for (Object event : transactionEvents) {
            for (TransactionalEventVisitorRegistration<?> registration : transactionalEventListeners) {
                if (registration.matches(event)) {
                    registration.afterCommit(event);
                }
            }
        }
    }

    private void dispatchAfterRollback() {
        for (Object event : List.copyOf(transactionEvents)) {
            for (TransactionalEventVisitorRegistration<?> registration : transactionalEventListeners) {
                if (registration.matches(event)) {
                    registration.afterRollback(event);
                }
            }
        }
    }

    private void dispatchAfterCompletion() {
        for (Object event : transactionEvents) {
            for (TransactionalEventVisitorRegistration<?> registration : transactionalEventListeners) {
                if (registration.matches(event)) {
                    registration.afterCompletion(event);
                }
            }
        }
    }

    private void dispatchFallbackEvent(Object event) {
        for (TransactionalEventVisitorRegistration<?> registration : transactionalEventListeners) {
            if (registration.matchesFallback(event)) {
                registration.afterCommit(event);
                registration.afterCompletion(event);
            }
        }
    }

    private void completeLocalTransaction() {
        try {
            if (localTransactionActive) {
                dispatchAfterCompletion();
            }
        } finally {
            localTransactionActive = false;
            transactionEvents.clear();
        }
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
        RelationSqlGenerator.RelationInsertParts parts =
                relationSql().buildRelationInsert(model, entity, omitPk, registry, deferred);
        BoundStatement bs = BoundStatement.of(parts.sql(), parts.parameters());
        LifecycleCallbacks.invoke(entity, LifecycleEvent.PRE_PERSIST);
        SqlExecutor.executeInsertReturning(connection, bs, model, entity);
        LifecycleCallbacks.invoke(entity, LifecycleEvent.POST_PERSIST);
    }

    @Override
    public int updateEntityRow(Object entity, EntityModel model) {
        return updateEntityRow(entity, model, List.of());
    }

    @Override
    public int updateEntityRow(Object entity, EntityModel model, List<RelationPersister.DeferredFkUpdate> deferred) {
        EntityHydrator.requirePkSet(entity, model.primaryKey());
        LifecycleCallbacks.invoke(entity, LifecycleEvent.PRE_UPDATE);
        int updated = SqlExecutor.executeUpdate(connection, relationSql().update(model, entity, registry, deferred));
        if (updated > 0) {
            LifecycleCallbacks.invoke(entity, LifecycleEvent.POST_UPDATE);
        }
        return updated;
    }

    @Override
    public void updateJoinColumn(Object entity, EntityModel model, ManyToOneField relation) {
        SqlExecutor.executeUpdate(connection, relationSql().updateJoinColumn(model, entity, relation, registry));
    }

    private RelationSqlGenerator relationSql() {
        if (sql instanceof RelationSqlGenerator relationSql) {
            return relationSql;
        }
        throw new MicroOrmException(
                "SqlGenerator " + sql.getClass().getName() + " does not support relation persistence");
    }

    @Override
    public int deleteEntityRow(Object entity, EntityModel model) {
        EntityHydrator.requirePkSet(entity, model.primaryKey());
        LifecycleCallbacks.invoke(entity, LifecycleEvent.PRE_REMOVE);
        int deleted = SqlExecutor.executeUpdate(connection, sql.delete(model, entity));
        if (deleted > 0) {
            LifecycleCallbacks.invoke(entity, LifecycleEvent.POST_REMOVE);
        }
        return deleted;
    }

    @Override
    public void deleteChildrenByOwner(OneToManyField relation, Object ownerPk) {
        EntityModel childModel = registry.get(relation.targetEntityClass());
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
            rows.forEach(child -> deleteEntityRow(child, childModel));
        }
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
        Set<Object> normalizedRetainedChildPks = retainedChildPks.stream()
                .map(pk -> normalizePk(childModel, pk))
                .collect(Collectors.toSet());
        try (Stream<?> rows = SqlExecutor.queryEntitiesStream(
                connection,
                sql.selectByJoinColumn(childModel, inverse.joinColumn(), jdbcOwnerPk),
                childModel,
                dialect,
                dialect.valueMapper(),
                lazyLoadContext(),
                registry)) {
            rows.forEach(existing -> {
                Object childPk = normalizePk(childModel, EntityHydrator.getFieldValue(existing, childModel.primaryKey()));
                if (!normalizedRetainedChildPks.contains(childPk)) {
                    deleteEntityRow(existing, childModel);
                }
            });
        }
    }

    private Object normalizePk(EntityModel model, Object value) {
        if (value == null) {
            return null;
        }
        EntityField pk = model.primaryKey();
        Object jdbcValue = dialect.valueMapper().toJdbcValue(pk, value);
        return dialect.valueMapper().fromJdbcValue(pk, jdbcValue);
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

    private record TransactionalEventVisitorRegistration<E>(
            Class<E> eventType,
            boolean fallbackExecution,
            TransactionalEventVisitor<? super E> visitor) {

        boolean matches(Object event) {
            return eventType.isInstance(event);
        }

        boolean matchesFallback(Object event) {
            return fallbackExecution && eventType.isInstance(event);
        }

        void beforeCommit(Object event) {
            visitor.beforeCommit(eventType.cast(event));
        }

        void afterCommit(Object event) {
            visitor.afterCommit(eventType.cast(event));
        }

        void afterRollback(Object event) {
            visitor.afterRollback(eventType.cast(event));
        }

        void afterCompletion(Object event) {
            visitor.afterCompletion(eventType.cast(event));
        }
    }
}
