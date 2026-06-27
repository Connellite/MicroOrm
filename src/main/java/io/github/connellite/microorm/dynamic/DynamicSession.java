package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.connection.ConnectionProvider;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dynamic.schema.DynamicSchemaManager;
import io.github.connellite.microorm.jdbc.SqlExecutor;
import io.github.connellite.microorm.sql.BoundStatement;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Session for runtime-defined tables: DDL (create/sync/drop) and Map-based CRUD.
 * Not thread-safe — use one session per thread, like {@link io.github.connellite.microorm.session.Session}.
 */
public final class DynamicSession implements AutoCloseable {

    private final Connection connection;
    private final ConnectionProvider provider;
    private final DynamicTableRegistry registry;
    private final Dialect dialect;
    private final DynamicSqlGenerator sql;
    private final DynamicSchemaManager schema;
    private final DynamicValueBinder valueBinder;
    private boolean closed;

    /** Internal constructor — use {@link io.github.connellite.microorm.MicroOrm#openDynamicSession()}. */
    public DynamicSession(
            Connection connection,
            ConnectionProvider provider,
            DynamicTableRegistry registry,
            Dialect dialect) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.sql = DynamicDialectSupport.sqlGenerator(dialect);
        this.schema = DynamicDialectSupport.schemaManager(dialect);
        this.valueBinder = new DynamicValueBinder(dialect);
    }

    /** JDBC connection used by this session. */
    public Connection connection() {
        return connection;
    }

    /** Creates the registered table when missing. Safe to call on every startup. */
    public void createTable(String tableName) throws SQLException {
        schema.createTable(connection, registry.get(tableName));
    }

    /** Adds missing nullable columns and indexes for the registered table. */
    public void syncTable(String tableName) throws SQLException {
        schema.syncTable(connection, registry.get(tableName));
    }

    /** Drops the registered table (destructive). */
    public void dropTable(String tableName) throws SQLException {
        schema.dropTable(connection, registry.get(tableName));
    }

    /** {@code true} when the physical table exists in the database. */
    public boolean tableExists(String tableName) throws SQLException {
        return schema.tableExists(connection, registry.get(tableName));
    }

    /**
     * Inserts one row. Column keys must match {@link Column#name()} on the registered table.
     *
     * @return affected row count
     */
    public int insert(String tableName, Map<String, ?> values) {
        BoundStatement stmt = sql.insert(registry.get(tableName), values);
        return SqlExecutor.executeUpdate(connection, stmt);
    }

    /**
     * Updates rows matching {@code where} with {@code set} values.
     *
     * @return affected row count
     */
    public int update(String tableName, Map<String, ?> set, Map<String, ?> where) {
        BoundStatement stmt = sql.update(registry.get(tableName), set, where);
        return SqlExecutor.executeUpdate(connection, stmt);
    }

    /**
     * Deletes rows matching {@code where}.
     *
     * @return affected row count
     */
    public int delete(String tableName, Map<String, ?> where) {
        BoundStatement stmt = sql.delete(registry.get(tableName), where);
        return SqlExecutor.executeUpdate(connection, stmt);
    }

    /** {@code true} when at least one row matches {@code where}. */
    public boolean exists(String tableName, Map<String, ?> where) {
        BoundStatement stmt = sql.exists(registry.get(tableName), where);
        return SqlExecutor.queryExists(connection, stmt);
    }

    /** Returns all rows from the registered table. */
    public List<Map<String, Object>> selectAll(String tableName) {
        DynamicTable table = registry.get(tableName);
        return SqlExecutor.queryMaps(connection, sql.selectAll(table), table, dialect, valueBinder);
    }

    /** Returns rows matching all equality filters. */
    public List<Map<String, Object>> select(String tableName, Map<String, ?> filters) {
        DynamicTable table = registry.get(tableName);
        return SqlExecutor.queryMaps(connection, sql.selectWhere(table, filters), table, dialect, valueBinder);
    }

    /** Returns the first matching row, or empty when none match. */
    public Optional<Map<String, Object>> selectOne(String tableName, Map<String, ?> filters) {
        List<Map<String, Object>> rows = select(tableName, filters);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw new io.github.connellite.microorm.exception.MicroOrmException(
                    "Expected at most one row, got " + rows.size() + " for dynamic table '" + tableName + "'");
        }
        return Optional.of(rows.get(0));
    }

    /** Shared registry of runtime table definitions. */
    public DynamicTableRegistry registry() {
        return registry;
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        provider.release(connection);
    }
}
