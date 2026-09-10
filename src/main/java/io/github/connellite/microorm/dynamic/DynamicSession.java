package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.connection.ConnectionProvider;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dynamic.schema.DynamicSchemaManager;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.generation.SequenceTarget;
import io.github.connellite.microorm.jdbc.SqlExecutor;
import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.util.UuidGenerators;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
        DynamicTable table = registry.get(tableName);
        Map<String, Object> effectiveValues = withGeneratedIds(table, values);
        requirePrimaryKeyForInsert(table, effectiveValues);
        BoundStatement stmt = sql.insert(table, effectiveValues);
        return SqlExecutor.executeUpdate(connection, stmt);
    }

    /**
     * Inserts one row and returns the primary key value. Generated keys are allocated or read
     * according to the table's primary key generation strategy.
     */
    public Object insertReturningId(String tableName, Map<String, ?> values) {
        DynamicTable table = registry.get(tableName);
        Map<String, Object> effectiveValues = withGeneratedIds(table, values);
        Column pk = table.primaryKey();
        boolean readIdentityKey = pk.autoIncrement() && isUnsetPk(values.get(pk.name()), pk);
        requirePrimaryKeyForInsert(table, effectiveValues);
        BoundStatement stmt = sql.insert(table, effectiveValues);
        if (readIdentityKey) {
            return SqlExecutor.executeDynamicInsertReturningKey(connection, stmt, pk, dialect);
        }
        SqlExecutor.executeUpdate(connection, stmt);
        Object id = effectiveValues.get(pk.name());
        if (isUnsetPk(id, pk)) {
            throw new MicroOrmException("Primary key value is required for dynamic table '" + table.name() + "'");
        }
        return id;
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

    /** Materializes rows matching a fluent dynamic SELECT. */
    public List<Map<String, Object>> selectRows(DynamicSelect query) {
        try (Stream<Map<String, Object>> rows = streamRows(query)) {
            return rows.toList();
        }
    }

    /** Returns exactly one row matching a fluent dynamic SELECT; throws when none or multiple rows match. */
    public Map<String, Object> selectOne(DynamicSelect query) {
        return singleResult(findAtMostTwo(query), true, query.tableName());
    }

    /** Returns zero or one row matching a fluent dynamic SELECT; throws when multiple rows match. */
    public Optional<Map<String, Object>> findOne(DynamicSelect query) {
        return Optional.ofNullable(singleResult(findAtMostTwo(query), false, query.tableName()));
    }

    /** Lazy dynamic-query row stream; must be closed. */
    public Stream<Map<String, Object>> streamRows(DynamicSelect query) {
        Objects.requireNonNull(query, "query");
        DynamicTable table = registry.get(query.tableName());
        return SqlExecutor.queryMapsStream(connection, sql.select(table, query), table, dialect, valueBinder);
    }

    /** Materializes custom-query rows using the registered dynamic table metadata. */
    public List<Map<String, Object>> selectRows(String tableName, Query query) {
        try (Stream<Map<String, Object>> rows = streamRows(tableName, query)) {
            return rows.toList();
        }
    }

    /** Lazy custom-query row stream using the registered dynamic table metadata; must be closed. */
    public Stream<Map<String, Object>> streamRows(String tableName, Query query) {
        Objects.requireNonNull(query, "query");
        DynamicTable table = registry.get(tableName);
        return SqlExecutor.queryMapsStream(connection, query, table, dialect, valueBinder);
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

    /** Executes a fluent dynamic UPDATE. */
    public int execute(DynamicUpdate update) {
        Objects.requireNonNull(update, "update");
        DynamicTable table = registry.get(update.tableName());
        return SqlExecutor.executeUpdate(connection, sql.update(table, update));
    }

    /** Executes a fluent dynamic DELETE. */
    public int execute(DynamicDelete delete) {
        Objects.requireNonNull(delete, "delete");
        DynamicTable table = registry.get(delete.tableName());
        return SqlExecutor.executeUpdate(connection, sql.delete(table, delete));
    }

    /** Shared registry of runtime table definitions. */
    public DynamicTableRegistry registry() {
        return registry;
    }

    private List<Map<String, Object>> findAtMostTwo(DynamicSelect query) {
        try (Stream<Map<String, Object>> rows = streamRows(query)) {
            return rows.limit(2).toList();
        }
    }

    private static Map<String, Object> singleResult(
            List<Map<String, Object>> rows,
            boolean requireOne,
            String tableName) {
        if (rows.isEmpty()) {
            if (requireOne) {
                throw new MicroOrmException("Expected one row, got 0 for dynamic table '" + tableName + "'");
            }
            return null;
        }
        if (rows.size() > 1) {
            throw new MicroOrmException("Expected at most one row, got " + rows.size()
                    + " for dynamic table '" + tableName + "'");
        }
        return rows.get(0);
    }

    private Map<String, Object> withGeneratedIds(DynamicTable table, Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        Map<String, Object> effectiveValues = new LinkedHashMap<>(values);
        Column pk = table.primaryKey();
        if (pk.uuidGenerated() && effectiveValues.get(pk.name()) == null) {
            effectiveValues.put(pk.name(), generateUuid(pk.idGeneration().uuidVersion()));
        }
        if (pk.sequenceGenerated() && isUnsetGeneratedPk(effectiveValues.get(pk.name()))) {
            if (!dialect.supportsSequences()) {
                throw new MicroOrmException("GenerationType.SEQUENCE is not supported by "
                        + dialect.getClass().getSimpleName() + " for dynamic table '" + table.name() + "'");
            }
            SequenceTarget target = new SequenceTarget(null, table.tableIdentifier(), pk.columnIdentifier(), pk.idGeneration());
            Object rawId = SqlExecutor.queryScalar(connection, Query.of(dialect.nextSequenceValueSql(target)), Object.class);
            Object id = valueBinder.fromJdbc(pk, rawId);
            if (id == null) {
                throw new MicroOrmException("Sequence returned NULL for dynamic table '" + table.name() + "'");
            }
            effectiveValues.put(pk.name(), id);
        }
        return effectiveValues;
    }

    private static void requirePrimaryKeyForInsert(DynamicTable table, Map<String, ?> values) {
        Column pk = table.primaryKey();
        Object value = values.get(pk.name());
        if (pk.autoIncrement() && isUnsetPk(value, pk)) {
            return;
        }
        if (isUnsetPk(value, pk)) {
            throw new MicroOrmException("Primary key value is required for dynamic table '" + table.name() + "'");
        }
    }

    private static boolean isUnsetPk(Object value, Column pk) {
        if (value == null) {
            return true;
        }
        Object databaseValue = pk.convertToDatabaseColumn(value);
        if (databaseValue == null) {
            return true;
        }
        if (databaseValue instanceof String s) {
            return s.isBlank();
        }
        return pk.idGeneration().generated() && isUnsetGeneratedPk(databaseValue);
    }

    private static boolean isUnsetGeneratedPk(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof Number number && number.longValue() == 0L;
    }

    private static UUID generateUuid(int version) {
        return switch (version) {
            case 1 -> UuidGenerators.generateVersion1();
            case 4 -> UuidGenerators.generateVersion4();
            case 6 -> UuidGenerators.generateVersion6();
            case 7 -> UuidGenerators.generateVersion7();
            default -> throw new MicroOrmException("Unsupported UUID version: " + version);
        };
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
