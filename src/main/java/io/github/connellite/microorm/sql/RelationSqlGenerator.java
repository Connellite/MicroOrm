package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.RelationPersister;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Optional SQL capability required for persisting entity relation graphs.
 * Custom dialect generators that support relation writes should implement this
 * instead of relying on a concrete {@link AbstractSqlGenerator} base class.
 */
public interface RelationSqlGenerator {

    RelationInsertParts buildRelationInsert(
            EntityModel model,
            Object entity,
            boolean omitPk,
            EntityModelRegistry registry,
            List<RelationPersister.DeferredFkUpdate> deferred);

    default RelationInsertParts buildRelationInsert(
            EntityModel model,
            Object entity,
            boolean omitPk,
            EntityModelRegistry registry,
            List<RelationPersister.DeferredFkUpdate> deferred,
            Set<Object> inserted) {
        return buildRelationInsert(model, entity, omitPk, registry, deferred);
    }

    default RelationInsertParts buildRelationInsert(
            EntityModel model,
            Object entity,
            boolean omitPk,
            EntityModelRegistry registry,
            List<RelationPersister.DeferredFkUpdate> deferred,
            Set<Object> inserted,
            Set<Object> inProgress) {
        return buildRelationInsert(model, entity, omitPk, registry, deferred, inserted);
    }

    default RelationInsertParts buildRelationInsert(
            EntityModel model,
            Object entity,
            boolean omitPk,
            EntityModelRegistry registry,
            List<RelationPersister.DeferredFkUpdate> deferred,
            Set<Object> inserted,
            Set<Object> inProgress,
            RelationWriteContext context) {
        return buildRelationInsert(model, entity, omitPk, registry, deferred, inserted, inProgress);
    }

    BoundStatement update(
            EntityModel model,
            Object entity,
            EntityModelRegistry registry,
            List<RelationPersister.DeferredFkUpdate> deferred);

    default BoundStatement update(
            EntityModel model,
            Object entity,
            EntityModelRegistry registry,
            List<RelationPersister.DeferredFkUpdate> deferred,
            RelationWriteContext context) {
        return update(model, entity, registry, deferred);
    }

    BoundStatement updateJoinColumn(
            EntityModel model,
            Object entity,
            ManyToOneField relation,
            EntityModelRegistry registry);


    record RelationInsertParts(String sql, Map<String, Object> parameters) {
    }
}
