package io.github.connellite.microorm.session;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.jdbc.EntityHydrator;
import io.github.connellite.microorm.jdbc.SqlExecutor;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class Session implements AutoCloseable {

    private final Connection connection;
    private final ConnectionProvider provider;
    private final EntityModelRegistry registry;
    private final SqlGenerator sql;
    private final Dialect dialect;

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

    public Connection connection() {
        return connection;
    }

    public void beginTransaction() throws SQLException {
        connection.setAutoCommit(false);
    }

    public void commitTransaction() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
        connection.setAutoCommit(true);
    }

    public void rollbackTransaction() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
        }
        connection.setAutoCommit(true);
    }

    public void createEntity(Class<?> entityClass) throws SQLException {
        EntityModel m = registry.register(entityClass);
        dialect.createTable(connection, m);
    }

    public void syncEntity(Class<?> entityClass) throws SQLException {
        EntityModel m = registry.register(entityClass);
        dialect.syncTable(connection, m);
    }

    public void updateEntity(Class<?> entityClass) throws SQLException {
        syncEntity(entityClass);
    }

    public void dropEntity(Class<?> entityClass) throws SQLException {
        EntityModel m = registry.register(entityClass);
        dialect.dropTable(connection, m);
    }

    public <T> T insertRow(T entity) {
        EntityModel m = registry.get(entity.getClass());
        assignGeneratedUuidIfNeeded(entity, m);
        BoundStatement bs = sql.insert(m, entity);
        SqlExecutor.executeInsertReturning(connection, bs, m, entity);
        return entity;
    }

    public <T> int insertRows(List<T> entities, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        Class<?> entityClass = entities.get(0).getClass();
        EntityModel m = registry.get(entityClass);
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

    public <T> int insertRows(List<T> entities) {
        return insertRows(entities, 200);
    }

    public int updateRow(Object entity) {
        EntityModel m = registry.get(entity.getClass());
        return SqlExecutor.executeUpdate(connection, sql.update(m, entity));
    }

    public int deleteRow(Object entity) {
        EntityModel m = registry.get(entity.getClass());
        return SqlExecutor.executeUpdate(connection, sql.delete(m, entity));
    }

    public int deleteById(Class<?> entityClass, Object id) {
        EntityModel m = registry.get(entityClass);
        return SqlExecutor.executeUpdate(connection, sql.deleteById(m, id));
    }

    public int deleteAllRows(Class<?> entityClass) {
        EntityModel m = registry.get(entityClass);
        String q = "DELETE FROM " + dialect.quote(m.tableName());
        return SqlExecutor.executeUpdate(connection, BoundStatement.of(q, java.util.Map.of()));
    }

    public boolean existsById(Class<?> type, Object id) {
        EntityModel m = registry.get(type);
        return SqlExecutor.queryExists(connection, sql.existsById(m, id));
    }

    public <T> T selectRow(Class<T> type, Object id) {
        EntityModel m = registry.get(type);
        try (Stream<T> rows = SqlExecutor.queryEntitiesStream(connection, sql.selectById(m, id), m, dialect.valueMapper())) {
            List<T> list = rows.toList();
            if (list.isEmpty()) {
                return null;
            }
            if (list.size() > 1) {
                throw new io.github.connellite.microorm.MicroOrmException("Expected at most one row, got " + list.size());
            }
            return list.get(0);
        }
    }

    public <T> List<T> selectRows(Class<T> type) {
        try (Stream<T> rows = streamRows(type)) {
            return rows.toList();
        }
    }

    public <T> Stream<T> streamRows(Class<T> type) {
        EntityModel m = registry.get(type);
        return SqlExecutor.queryEntitiesStream(connection, sql.selectAll(m), m, dialect.valueMapper());
    }

    public <T> List<T> selectRows(Class<T> type, Map<String, ?> filters) {
        try (Stream<T> rows = streamRows(type, filters)) {
            return rows.toList();
        }
    }

    public <T> Stream<T> streamRows(Class<T> type, Map<String, ?> filters) {
        EntityModel m = registry.get(type);
        return SqlExecutor.queryEntitiesStream(connection, sql.selectWhere(m, filters), m, dialect.valueMapper());
    }

    public <T> List<T> selectRows(Class<T> type, Query query) {
        try (Stream<T> rows = streamRows(type, query)) {
            return rows.toList();
        }
    }

    public <T> Stream<T> streamRows(Class<T> type, Query query) {
        EntityModel m = registry.get(type);
        return SqlExecutor.queryEntitiesStream(connection, query, m, dialect.valueMapper());
    }

    public int execute(Query query) {
        return SqlExecutor.executeUpdate(connection, query);
    }

    private static void assignGeneratedUuidIfNeeded(Object entity, EntityModel model) {
        if (model.primaryKey().autoIncrement()) {
            return;
        }
        if (model.primaryKey().javaType() == UUID.class && EntityHydrator.getFieldValue(entity, model.primaryKey()) == null) {
            EntityHydrator.setFieldValue(entity, model.primaryKey(), UUID.randomUUID());
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            provider.release(connection);
        }
    }
}
