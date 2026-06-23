package io.github.connellite.stoneorm.session;

import io.github.connellite.stoneorm.dialect.Dialect;
import io.github.connellite.stoneorm.jdbc.SqlExecutor;
import io.github.connellite.stoneorm.mapping.EntityModel;
import io.github.connellite.stoneorm.mapping.EntityModelRegistry;
import io.github.connellite.stoneorm.sql.BoundStatement;
import io.github.connellite.stoneorm.sql.SqlGenerator;
import io.github.connellite.stoneorm.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

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
        this.sql = new SqlGenerator(dialect);
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

    public void dropEntity(Class<?> entityClass) throws SQLException {
        EntityModel m = registry.register(entityClass);
        dialect.dropTable(connection, m);
    }

    public <T> T insertRow(T entity) {
        EntityModel m = registry.get(entity.getClass());
        BoundStatement bs = sql.insert(m, entity);
        SqlExecutor.executeInsertReturning(connection, bs, m, entity);
        return entity;
    }

    public int updateRow(Object entity) {
        EntityModel m = registry.get(entity.getClass());
        return SqlExecutor.executeUpdate(connection, sql.update(m, entity));
    }

    public int deleteRow(Object entity) {
        EntityModel m = registry.get(entity.getClass());
        return SqlExecutor.executeUpdate(connection, sql.delete(m, entity));
    }

    public int deleteAllRows(Class<?> entityClass) {
        EntityModel m = registry.get(entityClass);
        String q = "DELETE FROM " + dialect.quote(m.tableName());
        return SqlExecutor.executeUpdate(connection, BoundStatement.of(q, java.util.Map.of()));
    }

    public <T> T selectRow(Class<T> type, Object id) {
        EntityModel m = registry.get(type);
        return SqlExecutor.querySingle(connection, sql.selectById(m, id), m);
    }

    public <T> List<T> selectRows(Class<T> type) {
        EntityModel m = registry.get(type);
        return SqlExecutor.queryEntities(connection, sql.selectAll(m), m);
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
