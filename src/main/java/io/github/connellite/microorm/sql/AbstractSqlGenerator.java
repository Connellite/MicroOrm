package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.jdbc.EntityHydrator;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.RelationPersister;
import io.github.connellite.microorm.mapping.RelationValues;
import io.github.connellite.microorm.query.CompositeCriterion;
import io.github.connellite.microorm.query.Criterion;
import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.query.FieldCriterion;
import io.github.connellite.microorm.query.NotCriterion;
import io.github.connellite.microorm.query.Order;
import io.github.connellite.microorm.relation.LazyRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        return insertSql(model, omitPk, Set.of());
    }

    public String insertSql(EntityModel model, boolean omitPk, Set<String> omitJoinColumns) {
        List<String> colQuoted = new ArrayList<>();
        List<String> slots = new ArrayList<>();
        for (EntityField f : model.fields()) {
            if (omitPk && f.id()) {
                continue;
            }
            colQuoted.add(dialect.sqlName(f.columnIdentifier()));
            slots.add(":" + f.columnName());
        }
        for (ManyToOneField relation : model.manyToOneRelations()) {
            if (omitJoinColumns.contains(relation.joinColumn())) {
                continue;
            }
            colQuoted.add(dialect.sqlName(relation.joinColumnIdentifier()));
            slots.add(":" + relation.joinColumn());
        }
        return "INSERT INTO " + dialect.sqlName(model.tableIdentifier()) + " ("
                + String.join(", ", colQuoted) + ") VALUES (" + String.join(", ", slots) + ")";
    }

    public record RelationInsertParts(String sql, Map<String, Object> parameters) {
    }

    public RelationInsertParts buildRelationInsert(
            EntityModel model,
            Object entity,
            boolean omitPk,
            EntityModelRegistry registry,
            List<RelationPersister.DeferredFkUpdate> deferred) {
        Map<String, Object> named = new LinkedHashMap<>();
        Set<String> omitJoinColumns = new java.util.HashSet<>();
        for (EntityField f : model.fields()) {
            if (omitPk && f.id()) {
                continue;
            }
            named.put(f.columnName(), dialect.valueMapper().toJdbcValue(f, EntityHydrator.getFieldValue(entity, f)));
        }
        for (ManyToOneField relation : model.manyToOneRelations()) {
            Object value = resolveJoinColumnForWrite(entity, model, relation, registry, deferred);
            if (value == null && relation.nullable()) {
                omitJoinColumns.add(relation.joinColumn());
            } else {
                named.put(relation.joinColumn(), value);
            }
        }
        return new RelationInsertParts(insertSql(model, omitPk, omitJoinColumns), named);
    }

    @Override
    public Map<String, Object> insertParameters(EntityModel model, Object entity, boolean omitPk) {
        Map<String, Object> named = new LinkedHashMap<>();
        for (EntityField f : model.fields()) {
            if (omitPk && f.id()) {
                continue;
            }
            named.put(f.columnName(), dialect.valueMapper().toJdbcValue(f, EntityHydrator.getFieldValue(entity, f)));
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
            sets.add(dialect.sqlName(f.columnIdentifier()) + " = :" + f.columnName());
            params.put(f.columnName(), dialect.valueMapper().toJdbcValue(f, EntityHydrator.getFieldValue(entity, f)));
        }
        if (registry != null) {
            for (ManyToOneField relation : model.manyToOneRelations()) {
                sets.add(dialect.sqlName(relation.joinColumnIdentifier()) + " = :" + relation.joinColumn());
                params.put(relation.joinColumn(), resolveJoinColumnForWrite(entity, model, relation, registry, deferred));
            }
        }
        if (sets.isEmpty()) {
            throw new MicroOrmException("Entity has no updatable columns: " + model.entityClass().getName());
        }
        String pkName = pk.columnName();
        params.put(pkName, dialect.valueMapper().toJdbcValue(pk, EntityHydrator.getFieldValue(entity, pk)));
        String sql = "UPDATE " + dialect.sqlName(model.tableIdentifier()) + " SET " + String.join(", ", sets)
                + " WHERE " + dialect.sqlName(pk.columnIdentifier()) + " = :" + pkName;
        return BoundStatement.of(sql, params);
    }

    public BoundStatement updateJoinColumn(
            EntityModel model,
            Object entity,
            ManyToOneField relation,
            EntityModelRegistry registry) {
        EntityField pk = model.primaryKey();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(relation.joinColumn(), joinColumnJdbcValue(entity, relation, registry));
        String pkName = pk.columnName();
        params.put(pkName, dialect.valueMapper().toJdbcValue(pk, EntityHydrator.getFieldValue(entity, pk)));
        String sql = "UPDATE " + dialect.sqlName(model.tableIdentifier())
                + " SET " + dialect.sqlName(relation.joinColumnIdentifier()) + " = :" + relation.joinColumn()
                + " WHERE " + dialect.sqlName(pk.columnIdentifier()) + " = :" + pkName;
        return BoundStatement.of(sql, params);
    }

    private Object resolveJoinColumnForWrite(
            Object entity,
            EntityModel model,
            ManyToOneField relation,
            EntityModelRegistry registry,
            List<RelationPersister.DeferredFkUpdate> deferred) {
        LazyRef<?> ref = LazyRef.get(relation, entity);
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
                    deferred.add(new RelationPersister.DeferredFkUpdate(entity, model, relation));
                    return null;
                }
                throw new MicroOrmException("Cannot persist required @ManyToOne '" + relation.javaField().getName()
                        + "' before referenced entity has a primary key: " + model.entityClass().getName());
            }
        }
        EntityModel targetModel = registry.get(relation.targetEntityClass());
        Object raw = RelationValues.resolveRawForeignKey(ref, relation, registry);
        if (raw == null) {
            return null;
        }
        return dialect().valueMapper().toJdbcValue(targetModel.primaryKey(), raw);
    }

    private Object joinColumnJdbcValue(
            Object entity,
            ManyToOneField relation,
            EntityModelRegistry registry) {
        LazyRef<?> ref = LazyRef.get(relation, entity);
        if (ref == null) {
            return null;
        }
        Object raw = RelationValues.resolveRawForeignKey(ref, relation, registry);
        if (raw == null) {
            return null;
        }
        EntityModel targetModel = registry.get(relation.targetEntityClass());
        return dialect().valueMapper().toJdbcValue(targetModel.primaryKey(), raw);
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
                "DELETE FROM " + dialect.sqlName(model.tableIdentifier()) + " WHERE " + dialect.sqlName(pk.columnIdentifier()) + " = :" + pkName,
                params);
    }

    @Override
    public BoundStatement selectById(EntityModel model, Object id) {
        EntityField pk = model.primaryKey();
        String pkName = pk.columnName();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(pkName, dialect.valueMapper().toJdbcValue(pk, id));
        return BoundStatement.of(selectAllSql(model) + " WHERE " + dialect.sqlName(pk.columnIdentifier()) + " = :" + pkName, p);
    }

    @Override
    public BoundStatement existsById(EntityModel model, Object id) {
        EntityField pk = model.primaryKey();
        String pkName = pk.columnName();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(pkName, dialect.valueMapper().toJdbcValue(pk, id));
        String sql = "SELECT 1 FROM " + dialect.sqlName(model.tableIdentifier())
                + " WHERE " + dialect.sqlName(pk.columnIdentifier()) + " = :" + pkName;
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
            predicates.add(dialect.sqlName(field.columnIdentifier()) + " = :" + param);
            params.put(param, dialect.valueMapper().toJdbcValue(field, entry.getValue()));
        }
        return BoundStatement.of(selectAllSql(model) + " WHERE " + String.join(" AND ", predicates), params);
    }

    @Override
    public BoundStatement select(EntityModel model, EntityQuery<?> query) {
        if (query == null) {
            return selectAll(model);
        }
        if (query.entityType() != model.entityClass()) {
            throw new MicroOrmException("EntityQuery type " + query.entityType().getName()
                    + " does not match model " + model.entityClass().getName());
        }
        Map<String, Object> params = new LinkedHashMap<>();
        int[] paramCounter = {1};
        String sql = selectAllSql(model);
        if (query.criterion() != null) {
            sql += " WHERE " + renderCriterion(model, query.criterion(), params, paramCounter);
        }
        if (!query.orders().isEmpty()) {
            List<String> orderSql = new ArrayList<>();
            for (Order order : query.orders()) {
                EntityField field = fieldByName(model, order.fieldName());
                orderSql.add(dialect.sqlName(field.columnIdentifier()) + " " + order.direction().name());
            }
            sql += " ORDER BY " + String.join(", ", orderSql);
        }
        return BoundStatement.of(applyLimitOffset(
                sql,
                query.limit().isPresent() ? query.limit().getAsInt() : null,
                query.offset().isPresent() ? query.offset().getAsInt() : null,
                !query.orders().isEmpty()), params);
    }

    @Override
    public BoundStatement selectByJoinColumn(EntityModel model, String joinColumn, Object joinValue) {
        if (joinColumn == null || joinColumn.isBlank()) {
            throw new MicroOrmException("Join column name cannot be blank");
        }
        SqlIdentifier identifier = resolveJoinColumn(model, joinColumn);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(joinColumn, joinValue);
        return BoundStatement.of(
                selectAllSql(model) + " WHERE " + dialect.sqlName(identifier) + " = :" + joinColumn,
                params);
    }

    private static SqlIdentifier resolveJoinColumn(EntityModel model, String joinColumn) {
        for (ManyToOneField relation : model.manyToOneRelations()) {
            if (relation.joinColumn().equals(joinColumn)) {
                return relation.joinColumnIdentifier();
            }
        }
        return SqlIdentifier.unquoted(joinColumn);
    }

    protected abstract String limitOne(String sql);

    protected String applyLimitOffset(String sql, Integer limit, Integer offset, boolean hasOrder) {
        if (limit == null && (offset == null || offset == 0)) {
            return sql;
        }
        if (limit != null) {
            sql += " LIMIT " + limit;
        }
        if (offset != null && offset > 0) {
            if (limit == null) {
                sql += " LIMIT -1";
            }
            sql += " OFFSET " + offset;
        }
        return sql;
    }

    protected final Dialect dialect() {
        return dialect;
    }

    private String selectAllSql(EntityModel model) {
        List<String> cols = new ArrayList<>();
        for (EntityField f : model.fields()) {
            cols.add(dialect.sqlName(model.tableIdentifier()) + "." + dialect.sqlName(f.columnIdentifier()));
        }
        for (ManyToOneField relation : model.manyToOneRelations()) {
            cols.add(dialect.sqlName(model.tableIdentifier()) + "." + dialect.sqlName(relation.joinColumnIdentifier()));
        }
        return "SELECT " + String.join(", ", cols) + " FROM " + dialect.sqlName(model.tableIdentifier());
    }

    private String renderCriterion(
            EntityModel model,
            Criterion criterion,
            Map<String, Object> params,
            int[] paramCounter) {
        if (criterion instanceof FieldCriterion fieldCriterion) {
            return renderFieldCriterion(model, fieldCriterion, params, paramCounter);
        }
        if (criterion instanceof CompositeCriterion composite) {
            String separator = " " + composite.operator().name() + " ";
            List<String> rendered = new ArrayList<>();
            for (Criterion child : composite.criteria()) {
                rendered.add(renderCriterion(model, child, params, paramCounter));
            }
            return "(" + String.join(separator, rendered) + ")";
        }
        if (criterion instanceof NotCriterion notCriterion) {
            return "NOT (" + renderCriterion(model, notCriterion.criterion(), params, paramCounter) + ")";
        }
        throw new MicroOrmException("Unsupported criterion type: " + criterion.getClass().getName());
    }

    private String renderFieldCriterion(
            EntityModel model,
            FieldCriterion criterion,
            Map<String, Object> params,
            int[] paramCounter) {
        EntityField field = fieldByName(model, criterion.fieldName());
        String column = dialect.sqlName(field.columnIdentifier());
        return switch (criterion.kind()) {
            case COMPARISON -> renderComparison(field, column, criterion, params, paramCounter);
            case IN -> renderIn(field, column, criterion, params, paramCounter);
            case LIKE -> {
                String param = nextParam(paramCounter);
                params.put(param, dialect.valueMapper().toJdbcValue(field, criterion.value()));
                yield column + " LIKE :" + param;
            }
            case IS_NULL -> column + " IS NULL";
            case IS_NOT_NULL -> column + " IS NOT NULL";
        };
    }

    private String renderComparison(
            EntityField field,
            String column,
            FieldCriterion criterion,
            Map<String, Object> params,
            int[] paramCounter) {
        if (criterion.value() == null) {
            return criterion.operator() == io.github.connellite.microorm.query.ComparisonOperator.NE
                    ? column + " IS NOT NULL"
                    : column + " IS NULL";
        }
        String param = nextParam(paramCounter);
        params.put(param, dialect.valueMapper().toJdbcValue(field, criterion.value()));
        return column + " " + criterion.operator().sql() + " :" + param;
    }

    private String renderIn(
            EntityField field,
            String column,
            FieldCriterion criterion,
            Map<String, Object> params,
            int[] paramCounter) {
        List<String> placeholders = new ArrayList<>();
        for (Object value : criterion.values()) {
            if (value == null) {
                throw new MicroOrmException("IN criterion does not support null values for field: " + criterion.fieldName());
            }
            String param = nextParam(paramCounter);
            placeholders.add(":" + param);
            params.put(param, dialect.valueMapper().toJdbcValue(field, value));
        }
        return column + " IN (" + String.join(", ", placeholders) + ")";
    }

    private static String nextParam(int[] paramCounter) {
        return "p" + paramCounter[0]++;
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
