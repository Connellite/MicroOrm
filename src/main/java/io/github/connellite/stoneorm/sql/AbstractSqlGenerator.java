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

public abstract class AbstractSqlGenerator implements SqlGenerator {

    private final Dialect dialect;

    protected AbstractSqlGenerator(Dialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public BoundStatement insert(EntityModel model, Object entity) {
        EntityField pk = model.primaryKey();
        boolean omitPk = pk.autoIncrement() && EntityHydrator.isUnsetPk(entity, pk);
        return insert(model, entity, omitPk);
    }

    @Override
    public BoundStatement insert(EntityModel model, Object entity, boolean omitPk) {
        return BoundStatement.of(insertSql(model, omitPk), insertParameters(model, entity, omitPk));
    }

    @Override
    public String insertSql(EntityModel model, boolean omitPk) {
        List<String> colQuoted = new ArrayList<>();
        List<String> slots = new ArrayList<>();
        for (EntityField f : model.fields()) {
            if (omitPk && f.id()) {
                continue;
            }
            colQuoted.add(dialect.quote(f.columnName()));
            String pn = f.columnName();
            slots.add(":" + pn);
        }
        return "INSERT INTO " + dialect.quote(model.tableName()) + " ("
                + String.join(", ", colQuoted) + ") VALUES (" + String.join(", ", slots) + ")";
    }

    @Override
    public Map<String, Object> insertParameters(EntityModel model, Object entity, boolean omitPk) {
        Map<String, Object> named = new LinkedHashMap<>();
        for (EntityField f : model.fields()) {
            if (omitPk && f.id()) {
                continue;
            }
            named.put(f.columnName(), EntityHydrator.getFieldValue(entity, f));
        }
        return named;
    }

    @Override
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

    @Override
    public BoundStatement delete(EntityModel model, Object entity) {
        EntityField pk = model.primaryKey();
        return deleteById(model, EntityHydrator.getFieldValue(entity, pk));
    }

    @Override
    public BoundStatement deleteById(EntityModel model, Object id) {
        EntityField pk = model.primaryKey();
        String pkName = pk.columnName();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(pkName, id);
        return BoundStatement.of(
                "DELETE FROM " + dialect.quote(model.tableName()) + " WHERE " + dialect.quote(pkName) + " = :" + pkName,
                params);
    }

    @Override
    public BoundStatement selectById(EntityModel model, Object id) {
        EntityField pk = model.primaryKey();
        String pkName = pk.columnName();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(pkName, id);
        return BoundStatement.of(selectAllSql(model) + " WHERE " + dialect.quote(pkName) + " = :" + pkName, p);
    }

    @Override
    public BoundStatement existsById(EntityModel model, Object id) {
        EntityField pk = model.primaryKey();
        String pkName = pk.columnName();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(pkName, id);
        String sql = "SELECT 1 FROM " + dialect.quote(model.tableName())
                + " WHERE " + dialect.quote(pkName) + " = :" + pkName;
        return BoundStatement.of(limitOne(sql), p);
    }

    @Override
    public BoundStatement selectAll(EntityModel model) {
        return BoundStatement.of(selectAllSql(model), Map.of());
    }

    @Override
    public BoundStatement selectWhere(EntityModel model, Map<String, ?> filters) {
        if (filters == null || filters.isEmpty()) {
            return selectAll(model);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> predicates = new ArrayList<>();
        for (Map.Entry<String, ?> entry : filters.entrySet()) {
            EntityField field = fieldByName(model, entry.getKey());
            String param = field.columnName();
            if (params.containsKey(param)) {
                throw new StoneOrmException("Duplicate filter for column: " + param);
            }
            predicates.add(dialect.quote(field.columnName()) + " = :" + param);
            params.put(param, entry.getValue());
        }
        return BoundStatement.of(selectAllSql(model) + " WHERE " + String.join(" AND ", predicates), params);
    }

    protected abstract String limitOne(String sql);

    protected final Dialect dialect() {
        return dialect;
    }

    private String selectAllSql(EntityModel model) {
        List<String> cols = new ArrayList<>();
        for (EntityField f : model.fields()) {
            cols.add(dialect.quote(model.tableName()) + "." + dialect.quote(f.columnName()));
        }
        return "SELECT " + String.join(", ", cols) + " FROM " + dialect.quote(model.tableName());
    }

    private static EntityField fieldByName(EntityModel model, String name) {
        if (name == null || name.isBlank()) {
            throw new StoneOrmException("Filter field name cannot be blank");
        }
        for (EntityField f : model.fields()) {
            if (f.columnName().equals(name) || f.javaField().getName().equals(name)) {
                return f;
            }
        }
        throw new StoneOrmException("Unknown mapped field or column '" + name + "' on " + model.entityClass().getName());
    }
}
