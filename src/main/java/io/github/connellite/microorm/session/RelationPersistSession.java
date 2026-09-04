package io.github.connellite.microorm.session;

import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.mapping.ManyToManyField;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.OneToManyField;
import io.github.connellite.microorm.mapping.RelationPersister;

import java.util.List;
import java.util.Set;

/** Internal callbacks used by {@link io.github.connellite.microorm.mapping.RelationPersister}. */
public interface RelationPersistSession {

    EntityModelRegistry registry();

    void assignGeneratedIdsIfNeeded(Object entity, EntityModel model);

    void requirePkSet(Object entity, EntityModel model);

    Object pkValue(Object entity, EntityModel model);

    boolean existsByPrimaryKey(Object entity, EntityModel model);

    void insertEntityRow(
            Object entity,
            EntityModel model,
            List<RelationPersister.DeferredFkUpdate> deferred,
            Set<Object> inserted,
            Set<Object> inProgress);

    int updateEntityRow(Object entity, EntityModel model);

    int updateEntityRow(Object entity, EntityModel model, List<RelationPersister.DeferredFkUpdate> deferred);

    void updateJoinColumn(Object entity, EntityModel model, ManyToOneField relation);

    int deleteEntityRow(Object entity, EntityModel model);

    void deleteChildrenByOwner(OneToManyField relation, Object ownerPk);

    void deleteOrphanChildren(
            OneToManyField relation,
            Object ownerPk,
            Set<Object> retainedChildPks,
            EntityModel childModel);

    void replaceJoinTableLinks(ManyToManyField owning, Object ownerPk, Set<Object> targetPks);

    void deleteJoinTableLinks(ManyToManyField owning, Object ownerPk);

    void deleteJoinTableLinksByTarget(ManyToManyField owning, Object targetPk);
}
