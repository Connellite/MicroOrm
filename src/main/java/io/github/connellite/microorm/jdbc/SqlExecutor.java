package io.github.connellite.microorm.jdbc;

import io.github.connellite.collections.ListUtils;
import io.github.connellite.jdbc.NamedPreparedStatement;
import io.github.connellite.jdbc.NamedQuery;
import io.github.connellite.jdbc.ResultSetMetaDataUtils;
import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.microorm.type.JdbcValueMapper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class SqlExecutor {

    private SqlExecutor() {
    }

    public static int executeUpdate(Connection connection, BoundStatement stmt) {
        try (NamedPreparedStatement nps = prepare(connection, stmt)) {
            return nps.executeUpdate();
        } catch (SQLException e) {
            throw MicroOrmException.wrap(e);
        }
    }

    public static int executeUpdate(Connection connection, Query query) {
        try (NamedPreparedStatement nps = prepare(connection, query)) {
            return nps.executeUpdate();
        } catch (SQLException e) {
            throw MicroOrmException.wrap(e);
        }
    }

    public static boolean queryExists(Connection connection, BoundStatement stmt) {
        try (NamedPreparedStatement nps = prepare(connection, stmt);
             ResultSet rs = nps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            throw MicroOrmException.wrap(e);
        }
    }

    /**
     * Insert and apply generated keys to {@code entity} when the statement requests generated keys.
     */
    public static int executeInsertReturning(Connection connection, BoundStatement stmt, EntityModel model, Object entity) {
        try (NamedPreparedStatement nps = prepareInsertReturning(connection, stmt.sql(), model)) {
            nps.setAll(stmt.parameters());
            int n = nps.executeUpdate();
            if (model.primaryKey().autoIncrement()) {
                try (ResultSet keys = nps.unwrap().getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new MicroOrmException("INSERT did not return generated keys for " + model.entityClass().getName());
                    }
                    applyGeneratedKey(entity, model, keys);
                }
            }
            return n;
        } catch (SQLException e) {
            throw MicroOrmException.wrap(e);
        }
    }

    public static int executeBatchInsertReturning(
            Connection connection,
            String sql,
            List<Map<String, Object>> rows,
            int batchSize,
            EntityModel model,
            List<?> entities) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (model.primaryKey().autoIncrement()
                && (!JdbcDatabaseSupport.supportsBatchGeneratedKeys(connection)
                || JdbcDatabaseSupport.isSqlite(connection))) {
            return executeSequentialInsertReturning(connection, sql, rows, model, entities);
        }
        int chunkSize = batchSize <= 0 ? 200 : batchSize;
        int total = 0;
        try (NamedPreparedStatement nps = prepareInsertReturning(connection, sql, model)) {
            int chunkStart = 0;
            for (List<Map<String, Object>> chunk : ListUtils.splitIntoChunksBySize(rows, chunkSize)) {
                for (Map<String, Object> row : chunk) {
                    nps.clearParameters();
                    nps.setAll(row);
                    nps.addBatch();
                }
                total += executeBatchAndApplyKeys(connection, nps, model, entities, chunkStart, chunk.size());
                nps.unwrap().clearBatch();
                chunkStart += chunk.size();
            }
            return total;
        } catch (SQLException e) {
            throw MicroOrmException.wrap(e);
        }
    }

    private static int executeSequentialInsertReturning(
            Connection connection,
            String sql,
            List<Map<String, Object>> rows,
            EntityModel model,
            List<?> entities) {
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            try (NamedPreparedStatement nps = prepareInsertReturning(connection, sql, model)) {
                nps.setAll(rows.get(i));
                total += nps.executeUpdate();
                if (model.primaryKey().autoIncrement()) {
                    try (ResultSet keys = nps.unwrap().getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new MicroOrmException("INSERT did not return generated keys for "
                                    + model.entityClass().getName());
                        }
                        applyGeneratedKey(entities.get(i), model, keys);
                    }
                }
            } catch (SQLException e) {
                throw MicroOrmException.wrap(e);
            }
        }
        return total;
    }

    private static int executeBatchAndApplyKeys(
            Connection connection,
            NamedPreparedStatement nps,
            EntityModel model,
            List<?> entities,
            int entityOffset,
            int count) throws SQLException {
        int total = affectedRows(nps.executeBatch());
        if (model.primaryKey().autoIncrement()) {
            int applied = 0;
            try (ResultSet keys = nps.unwrap().getGeneratedKeys()) {
                while (applied < count && keys.next()) {
                    applyGeneratedKey(entities.get(entityOffset + applied), model, keys);
                    applied++;
                }
            }
            if (applied < count) {
                throw new MicroOrmException("Batch INSERT returned " + applied + " generated keys, expected " + count
                        + " for " + model.entityClass().getName());
            }
        }
        return total;
    }

    private static int affectedRows(int[] counts) {
        int total = 0;
        for (int count : counts) {
            if (count > 0) {
                total += count;
            } else if (count == Statement.SUCCESS_NO_INFO) {
                total += 1;
            }
        }
        return total;
    }

    private static void applyGeneratedKey(Object entity, EntityModel model, ResultSet keys) throws SQLException {
        var pk = model.primaryKey();
        Object key = keys.getObject(1);
        if (key == null || keys.wasNull()) {
            throw new MicroOrmException("Generated key was NULL for " + model.entityClass().getName());
        }
        EntityHydrator.setFieldValue(entity, pk, key);
    }

    public static <T> List<T> queryEntities(Connection connection, BoundStatement stmt, EntityModel model) {
        try (Stream<T> stream = queryEntitiesStream(connection, stmt, model, null)) {
            return stream.toList();
        }
    }

    public static <T> List<T> queryEntities(Connection connection, Query query, EntityModel model) {
        try (Stream<T> stream = queryEntitiesStream(connection, query, model, null)) {
            return stream.toList();
        }
    }

    public static <T> Stream<T> queryEntitiesStream(Connection connection, BoundStatement stmt, EntityModel model) {
        return queryEntitiesStream(connection, stmt, model, null);
    }

    public static <T> Stream<T> queryEntitiesStream(
            Connection connection,
            BoundStatement stmt,
            EntityModel model,
            JdbcValueMapper valueMapper) {
        try {
            NamedPreparedStatement nps = prepare(connection, stmt);
            ResultSet rs = nps.executeQuery();
            return ResultSetEntityStream.stream(nps, rs, model, null, valueMapper);
        } catch (SQLException e) {
            throw MicroOrmException.wrap(e);
        }
    }

    public static <T> Stream<T> queryEntitiesStream(Connection connection, Query query, EntityModel model) {
        return queryEntitiesStream(connection, query, model, null);
    }

    public static <T> Stream<T> queryEntitiesStream(
            Connection connection,
            Query query,
            EntityModel model,
            JdbcValueMapper valueMapper) {
        try {
            NamedPreparedStatement nps = prepare(connection, query);
            ResultSet rs = nps.executeQuery();
            Collection<String> columnLabels = ResultSetMetaDataUtils.getColumnLabels(rs);
            return ResultSetEntityStream.stream(nps, rs, model, columnLabels, valueMapper);
        } catch (SQLException e) {
            throw MicroOrmException.wrap(e);
        }
    }

    public static <T> T querySingle(Connection connection, BoundStatement stmt, EntityModel model) {
        List<T> rows = queryEntities(connection, stmt, model);
        if (rows.isEmpty()) {
            return null;
        }
        if (rows.size() > 1) {
            throw new MicroOrmException("Expected at most one row, got " + rows.size());
        }
        return rows.get(0);
    }

    private static NamedPreparedStatement prepareInsertReturning(Connection connection, String sql, EntityModel model)
            throws SQLException {
        if (model.primaryKey().autoIncrement() && JdbcDatabaseSupport.requiresGeneratedKeyColumnNames(connection)) {
            return NamedPreparedStatement.of(connection, sql, JdbcDatabaseSupport.oracleGeneratedKeyColumnNames(model));
        }
        return NamedPreparedStatement.of(connection, sql, Statement.RETURN_GENERATED_KEYS);
    }

    private static NamedPreparedStatement prepare(Connection connection, BoundStatement stmt) throws SQLException {
        return NamedQuery.of(stmt.sql()).setAll(stmt.parameters()).prepare(connection);
    }

    private static NamedPreparedStatement prepare(Connection connection, Query query) throws SQLException {
        NamedQuery namedQuery = NamedQuery.of(query.sql()).setAll(query.parameters());
        for (Map.Entry<String, java.util.Collection<?>> entry : query.collectionParameters().entrySet()) {
            namedQuery.setCollection(entry.getKey(), entry.getValue());
        }
        return namedQuery.prepare(connection);
    }
}
