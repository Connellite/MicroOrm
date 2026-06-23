package io.github.connellite.stoneorm.jdbc;

import io.github.connellite.stoneorm.StoneOrmException;
import io.github.connellite.stoneorm.mapping.EntityModel;
import io.github.connellite.stoneorm.sql.BoundStatement;

import io.github.connellite.jdbc.NamedPreparedStatement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class SqlExecutor {

    private SqlExecutor() {
    }

    public static int executeUpdate(Connection connection, BoundStatement stmt) {
        try (NamedPreparedStatement nps = new NamedPreparedStatement(connection, stmt.sql())) {
            nps.setAll(stmt.parameters());
            return nps.executeUpdate();
        } catch (SQLException e) {
            throw StoneOrmException.wrap(e);
        }
    }

    /**
     * Insert and apply generated keys to {@code entity} when the statement requests generated keys.
     */
    public static int executeInsertReturning(Connection connection, BoundStatement stmt, EntityModel model, Object entity) {
        try (NamedPreparedStatement nps = new NamedPreparedStatement(
                connection, stmt.sql(), Statement.RETURN_GENERATED_KEYS)) {
            nps.setAll(stmt.parameters());
            int n = nps.executeUpdate();
            if (model.primaryKey().autoIncrement()) {
                try (ResultSet keys = nps.unwrap().getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new StoneOrmException("INSERT did not return generated keys for " + model.entityClass().getName());
                    }
                    applyGeneratedKey(entity, model, keys);
                }
            }
            return n;
        } catch (SQLException e) {
            throw StoneOrmException.wrap(e);
        }
    }

    private static void applyGeneratedKey(Object entity, EntityModel model, ResultSet keys) throws SQLException {
        var pk = model.primaryKey();
        Class<?> t = pk.javaType();
        if (t == long.class || t == Long.class) {
            long v = keys.getLong(1);
            if (keys.wasNull()) {
                throw new StoneOrmException("Generated key was NULL for " + model.entityClass().getName());
            }
            EntityHydrator.setFieldValue(entity, pk, v);
        } else if (t == int.class || t == Integer.class) {
            int v = keys.getInt(1);
            if (keys.wasNull()) {
                throw new StoneOrmException("Generated key was NULL for " + model.entityClass().getName());
            }
            EntityHydrator.setFieldValue(entity, pk, v);
        } else if (t == short.class || t == Short.class) {
            short v = keys.getShort(1);
            if (keys.wasNull()) {
                throw new StoneOrmException("Generated key was NULL for " + model.entityClass().getName());
            }
            EntityHydrator.setFieldValue(entity, pk, v);
        } else {
            Object key = keys.getObject(1);
            if (key == null || keys.wasNull()) {
                throw new StoneOrmException("Generated key was NULL for " + model.entityClass().getName());
            }
            EntityHydrator.setFieldValue(entity, pk, key);
        }
    }

    public static <T> List<T> queryEntities(Connection connection, BoundStatement stmt, EntityModel model) {
        try (NamedPreparedStatement nps = new NamedPreparedStatement(connection, stmt.sql())) {
            nps.setAll(stmt.parameters());
            try (ResultSet rs = nps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(EntityHydrator.mapRow(model, rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw StoneOrmException.wrap(e);
        }
    }

    public static <T> T querySingle(Connection connection, BoundStatement stmt, EntityModel model) {
        List<T> rows = queryEntities(connection, stmt, model);
        if (rows.isEmpty()) {
            return null;
        }
        if (rows.size() > 1) {
            throw new StoneOrmException("Expected at most one row, got " + rows.size());
        }
        return rows.get(0);
    }
}
