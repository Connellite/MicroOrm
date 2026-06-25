package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.jdbc.EntityHydrator;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.RelationValues;

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
            slots.add(":" + f.columnName());
        }
        for (ManyToOneField relation : model.manyToOneRelations()) {
            colQuoted.add(dialect.quote(relation.joinColumn()));
            slots.add(":" + relation.joinColumn());
        }
        return "INSERT INTO " + dialect.quote(model.tableName()) + " ("
                + String.join(", ", colQuoted) + ") VALUES (" + String.join(", ", slots) + ")";
    }

    @Override
    public Map<String, Object> insertParameters(EntityModel model, Object entity, boolean omitPk) {
        return insertParameters(model, entity, omitPk, null, null);
    }

    public Map<String, Object> insertParameters(
            EntityModel model,
            Object entity,
            boolean omitPk,
            EntityModelRegistry registry,
            List<io.github.connellite.microorm.mapping.RelationPersister.DeferredFkUpdate> deferred) {
        Map<String, Object> named = new LinkedHashMap<>();
        for (EntityField f : model.fields()) {
            if (omitPk && f.id()) {
                continue;
            }
            named.put(f.columnName(), dialect.valueMapper().toJdbcValue(f, EntityHydrator.getFieldValue(entity, f)));
        }
        if (registry != null) {
            for (ManyToOneField relation : model.manyToOneRelations()) {
                Object value = resolveJoinColumnForWrite(entity, model, relation, registry, deferred);
                named.put(relation.joinColumn(), value);
            }
        }
        return named;
    }

    @Override
    public BoundStatement update(EntityModel model, Object entity) {
        return update(model, entity, null, null);
    }

    public BoundStatement update(
            EntityModel model,
            Object entity,
            EntityModelRegistry registry,
            List<io.github.connellite.microorm.mapping.RelationPersister.DeferredFkUpdate> deferred) {
        EntityField pk = model.primaryKey();
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> sets = new ArrayList<>();
        for (EntityField f : model.fields()) {
            if (f.id()) {
                continue;
            }
            sets.add(dialect.quote(f.columnName()) + " = :" + f.columnName());
            params.put(f.columnName(), dialect.valueMapper().toJdbcValue(f, EntityHydrator.getFieldValue(entity, f)));
        }
        if (registry != null) {
            for (ManyToOneField relation : model.manyToOneRelations()) {
                sets.add(dialect.quote(relation.joinColumn()) + " = :" + relation.joinColumn());
                params.put(relation.joinColumn(), resolveJoinColumnForWrite(entity, model, relation, registry, deferred));
            }
        }
        if (sets.isEmpty()) {
            throw new MicroOrmException("Entity has no updatable columns: " + model.entityClass().getName());
        }
        String pkName = pk.columnName();
        params.put(pkName, dialect.valueMapper().toJdbcValue(pk, EntityHydrator.getFieldValue(entity, pk)));
        String sql = "UPDATE " + dialect.quote(model.tableName()) + " SET " + String.join(", ", sets)
                + " WHERE " + dialect.quote(pkName) + " = :" + pkName;
        return BoundStatement.of(sql, params);
    }

    public BoundStatement updateJoinColumn(
            EntityModel model,
            Object entity,
            ManyToOneField relation,
            EntityModelRegistry registry) {
        EntityField pk = model.primaryKey();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(relation.joinColumn(), RelationValues.joinColumnValue(entity, model, relation, registry, dialect()));
        String pkName = pk.columnName();
        params.put(pkName, dialect.valueMapper().toJdbcValue(pk, EntityHydrator.getFieldValue(entity, pk)));
        String sql = "UPDATE " + dialect.quote(model.tableName())
                + " SET " + dialect.quote(relation.joinColumn()) + " = :" + relation.joinColumn()
                + " WHERE " + dialect.quote(pkName) + " = :" + pkName;
        return BoundStatement.of(sql, params);
    }

    private Object resolveJoinColumnForWrite(
            Object entity,
            EntityModel model,
            ManyToOneField relation,
            EntityModelRegistry registry,
            List<io.github.connellite.microorm.mapping.RelationPersister.DeferredFkUpdate> deferred) {
        io.github.connellite.microorm.relation.LazyRef<?> ref =
                io.github.connellite.microorm.relation.LazyRef.get(relation, entity);
        if (ref == null) {
            if (!relation.nullable()) {
                throw new MicroOrmException("Required @ManyToOne '" + relation.javaField().getName()
                        + "' is null on " + model.entityClass().getName());
            }
            return null;
        }
        Object attached = ref.attachedEntity();
        if (attached != null) {
            EntityModel targetModel = registry.get(relation.targetEntityClass());
            if (RelationValues.isNew(attached, targetModel)) {
                if (deferred != null && relation.nullable()) {
                    deferred.add(new io.github.connellite.microorm.mapping.RelationPersister.DeferredFkUpdate(
                            entity, model, relation));
                    return null;
                }
                throw new MicroOrmException("Cannot persist required @ManyToOne '" + relation.javaField().getName()
                        + "' before referenced entity has a primary key: " + model.entityClass().getName());
            }
        }
        return RelationValues.joinColumnValue(entity, model, relation, registry, dialect());
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
        params.put(pkName, dialect.valueMapper().toJdbcValue(pk, id));
        return BoundStatement.of(
                "DELETE FROM " + dialect.quote(model.tableName()) + " WHERE " + dialect.quote(pkName) + " = :" + pkName,
                params);
    }

    @Override
    public BoundStatement selectById(EntityModel model, Object id) {
        EntityField pk = model.primaryKey();
        String pkName = pk.columnName();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(pkName, dialect.valueMapper().toJdbcValue(pk, id));
        return BoundStatement.of(selectAllSql(model) + " WHERE " + dialect.quote(pkName) + " = :" + pkName, p);
    }

    @Override
    public BoundStatement existsById(EntityModel model, Object id) {
        EntityField pk = model.primaryKey();
        String pkName = pk.columnName();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(pkName, dialect.valueMapper().toJdbcValue(pk, id));
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
                throw new MicroOrmException("Duplicate filter for column: " + param);
            }
            predicates.add(dialect.quote(field.columnName()) + " = :" + param);
            params.put(param, dialect.valueMapper().toJdbcValue(field, entry.getValue()));
        }
        return BoundStatement.of(selectAllSql(model) + " WHERE " + String.join(" AND ", predicates), params);
    }

    @Override
    public BoundStatement selectByJoinColumn(EntityModel model, String joinColumn, Object joinValue) {
        if (joinColumn == null || joinColumn.isBlank()) {
            throw new MicroOrmException("Join column name cannot be blank");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(joinColumn, joinValue);
        return BoundStatement.of(
                selectAllSql(model) + " WHERE " + dialect.quote(joinColumn) + " = :" + joinColumn,
                params);
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
        for (ManyToOneField relation : model.manyToOneRelations()) {
            cols.add(dialect.quote(model.tableName()) + "." + dialect.quote(relation.joinColumn()));
        }
        return "SELECT " + String.join(", ", cols) + " FROM " + dialect.quote(model.tableName());
    }

    private static EntityField fieldByName(EntityModel model, String name) {
        if (name == null || name.isBlank()) {
            throw new MicroOrmException("Filter field name cannot be blank");
        }
        for (EntityField f : model.fields()) {
            if (f.columnName().equals(name) || f.javaField().getName().equals(name)) {
                return f;
            }
        }
        throw new MicroOrmException("Unknown mapped field or column '" + name + "' on " + model.entityClass().getName());
    }
}
