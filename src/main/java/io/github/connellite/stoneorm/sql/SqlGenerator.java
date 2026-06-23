package io.github.connellite.stoneorm.sql;

import io.github.connellite.stoneorm.StoneOrmException;
import io.github.connellite.stoneorm.dialect.Dialect;
import io.github.connellite.stoneorm.jdbc.EntityHydrator;
import io.github.connellite.stoneorm.mapping.EntityField;
import io.github.connellite.stoneorm.mapping.EntityModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SqlGenerator {

    private final Dialect dialect;

    public SqlGenerator(Dialect dialect) {
        this.dialect = dialect;
    }

    public BoundStatement insert(EntityModel model, Object entity) {
        EntityField pk = model.primaryKey();
        boolean omitPk = pk.autoIncrement() && EntityHydrator.isUnsetPk(entity, pk);
        List<String> colQuoted = new ArrayList<>();
        List<String> slots = new ArrayList<>();
        Map<String, Object> named = new LinkedHashMap<>();
        for (EntityField f : model.fields()) {
            if (omitPk && f.id()) {
                continue;
            }
            colQuoted.add(dialect.quote(f.columnName()));
            String pn = f.columnName();
            slots.add(":" + pn);
            named.put(pn, EntityHydrator.getFieldValue(entity, f));
        }
        String sql = "INSERT INTO " + dialect.quote(model.tableName()) + " ("
                + String.join(", ", colQuoted) + ") VALUES (" + String.join(", ", slots) + ")";
        return BoundStatement.of(sql, named);
    }

    public BoundStatement update(EntityModel model, Object entity) {
        EntityField pk = model.primaryKey();
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> sets = new ArrayList<>();
        for (EntityField f : model.fields()) {
            if (f.id()) {
                continue;
            }
            sets.add(dialect.quote(f.columnName()) + " = :" + f.columnName());
            params.put(f.columnName(), EntityHydrator.getFieldValue(entity, f));
        }
        String pkName = pk.columnName();
        params.put(pkName, EntityHydrator.getFieldValue(entity, pk));
        String sql = "UPDATE " + dialect.quote(model.tableName()) + " SET " + String.join(", ", sets)
                + " WHERE " + dialect.quote(pkName) + " = :" + pkName;
        return BoundStatement.of(sql, params);
    }

    public BoundStatement delete(EntityModel model, Object entity) {
        EntityField pk = model.primaryKey();
        Map<String, Object> params = new LinkedHashMap<>();
        String pkName = pk.columnName();
        params.put(pkName, EntityHydrator.getFieldValue(entity, pk));
        String sql = "DELETE FROM " + dialect.quote(model.tableName())
                + " WHERE " + dialect.quote(pkName) + " = :" + pkName;
        return BoundStatement.of(sql, params);
    }

    public BoundStatement selectById(EntityModel model, Object id) {
        EntityField pk = model.primaryKey();
        String pkName = pk.columnName();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(pkName, id);
        return BoundStatement.of(selectAllSql(model) + " WHERE " + dialect.quote(pkName) + " = :" + pkName, p);
    }

    public BoundStatement selectAll(EntityModel model) {
        return BoundStatement.of(selectAllSql(model), Map.of());
    }

    private String selectAllSql(EntityModel model) {
        List<String> cols = new ArrayList<>();
        for (EntityField f : model.fields()) {
            cols.add(dialect.quote(model.tableName()) + "." + dialect.quote(f.columnName()));
        }
        return "SELECT " + String.join(", ", cols) + " FROM " + dialect.quote(model.tableName());
    }

    public static void validateColumnNames(EntityModel model) {
        for (EntityField f : model.fields()) {
            String n = f.columnName();
            if (!n.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new StoneOrmException("Invalid SQL column / parameter name (use [a-zA-Z_][a-zA-Z0-9_]*): " + n);
            }
        }
    }
}
