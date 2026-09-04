package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.annotation.CascadeType;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.relation.EagerRef;
import io.github.connellite.microorm.relation.EntityCollection;
import io.github.connellite.microorm.relation.EntityRef;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.session.RelationPersistSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Persist / merge / remove for relation graphs, following Hibernate's cascade points
 * and ActionQueue flush order so foreign-key constraints stay valid:
 * <ul>
 *   <li>{@code persist}: cascade many-to-one before insert (parents first), then insert,
 *       then cascade collections (children after the FK owner exists)</li>
 *   <li>nullable FKs to a not-yet-inserted target are left {@code NULL} and updated after
 *       both rows exist (Hibernate unresolved entity-insert / two-pass cycle handling)</li>
 *   <li>required FKs to a transient or still-pending target fail like Hibernate
 *       {@code TransientPropertyValueException} instead of inserting a violating row</li>
 *   <li>{@code remove}: delete cascaded children first, then the owner</li>
 * </ul>
 * {@code insertRow} is persist, {@code updateRow} is merge, {@code deleteRow} is remove.
 * Associations cascade only when {@link CascadeType} is set; {@code orphanRemoval} is independent.
 *
 * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/AbstractSaveEventListener.java#L264">AbstractSaveEventListener.performSaveOrReplicate</a>
 * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/internal/CascadePoint.java">CascadePoint</a>
 * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/internal/Cascade.java#L72">Cascade.cascade</a>
 * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/spi/ActionQueue.java">ActionQueue</a>
 */
public final class RelationPersister {

    private RelationPersister() {
    }

    /**
     * Hibernate persist of a transient instance.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultPersistEventListener.java#L61">DefaultPersistEventListener.onPersist</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultPersistEventListener.java#L139">entityIsTransient</a>
     */
    public static <T> T insert(RelationPersistSession session, T entity) {
        List<DeferredFkUpdate> deferred = new ArrayList<>();
        Set<Object> inProgress = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> inserted = Collections.newSetFromMap(new IdentityHashMap<>());
        persist(session, entity, inProgress, inserted, deferred);
        applyDeferredFkUpdates(session, deferred);
        return entity;
    }

    /**
     * Hibernate merge of a managed / already-persisted instance, then association cascades.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultMergeEventListener.java#L257">DefaultMergeEventListener.entityIsPersistent</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultMergeEventListener.java#L618">cascadeOnMerge</a>
     */
    public static int update(RelationPersistSession session, Object entity) {
        EntityModel model = session.registry().get(entity.getClass());
        int rows = session.updateEntityRow(entity, model, List.of());
        List<DeferredFkUpdate> deferred = new ArrayList<>();
        Set<Object> inProgress = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> inserted = Collections.newSetFromMap(new IdentityHashMap<>());
        inProgress.add(entity);
        inserted.add(entity);
        mergeAssociations(session, entity, model, inProgress, inserted, deferred);
        applyDeferredFkUpdates(session, deferred);
        return rows;
    }

    /**
     * Hibernate remove: cascade collections first, delete the owner, then cascade many-to-one.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultDeleteEventListener.java#L363">DefaultDeleteEventListener.deleteEntity</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultDeleteEventListener.java#L491">cascadeBeforeDelete</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultDeleteEventListener.java#L518">cascadeAfterDelete</a>
     */
    public static int delete(RelationPersistSession session, Object entity) {
        EntityModel model = session.registry().get(entity.getClass());
        session.requirePkSet(entity, model);
        Object ownerPk = session.pkValue(entity, model);
        for (OneToManyField relation : model.oneToManyRelations()) {
            if (relation.cascades(CascadeType.REMOVE) || relation.orphanRemoval()) {
                session.deleteChildrenByOwner(relation, ownerPk);
            }
        }
        List<Object> cascadedManyToMany = collectManyToManyRemoveTargets(session, entity, model);
        unlinkManyToMany(session, entity, model, ownerPk);
        for (Object child : cascadedManyToMany) {
            delete(session, child);
        }
        int deleted = session.deleteEntityRow(entity, model);
        for (ManyToOneField relation : model.manyToOneRelations()) {
            if (!relation.cascades(CascadeType.REMOVE)) {
                continue;
            }
            EntityRef<?> ref = EntityRef.get(relation, entity);
            Object attached = ref == null ? null : ref.attachedEntity();
            if (attached != null) {
                delete(session, attached);
            }
        }
        return deleted;
    }

    /**
     * Hibernate {@code performSaveOrReplicate}: {@code cascadeBeforeSave} → insert → {@code cascadeAfterSave}.
     * {@code inProgress} is the {@code Status.SAVING} placeholder that stops recursive persist of the same instance.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/AbstractSaveEventListener.java#L264">AbstractSaveEventListener.performSaveOrReplicate</a>
     */
    private static void persist(
            RelationPersistSession session,
            Object entity,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        if (!inProgress.add(entity)) {
            return;
        }
        if (inserted.contains(entity)) {
            return;
        }
        EntityModel model = session.registry().get(entity.getClass());
        cascadePersistManyToOnes(session, entity, model, inProgress, inserted, deferred);
        session.assignGeneratedIdsIfNeeded(entity, model);
        session.insertEntityRow(entity, model, deferred, inserted, inProgress);
        inserted.add(entity);
        cascadePersistCollections(session, entity, model, inProgress, inserted, deferred);
    }

    /**
     * Hibernate {@code cascadeBeforeSave}: persist many-to-one before the owner insert
     * ({@code CascadePoint.BEFORE_INSERT_AFTER_DELETE}). A required transient target without
     * {@code CascadeType.PERSIST} fails like {@code TransientPropertyValueException}.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/AbstractSaveEventListener.java#L471">AbstractSaveEventListener.cascadeBeforeSave</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/internal/CascadePoint.java#L23">CascadePoint.BEFORE_INSERT_AFTER_DELETE</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/TransientPropertyValueException.java">TransientPropertyValueException</a>
     */
    private static void cascadePersistManyToOnes(
            RelationPersistSession session,
            Object entity,
            EntityModel model,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        for (ManyToOneField relation : model.manyToOneRelations()) {
            EntityRef<?> ref = EntityRef.get(relation, entity);
            if (ref == null) {
                continue;
            }
            Object attached = ref.attachedEntity();
            if (attached == null || inserted.contains(attached) || inProgress.contains(attached)) {
                continue;
            }
            EntityModel targetModel = session.registry().get(relation.targetEntityClass());
            boolean transientTarget = RelationValues.isNew(attached, targetModel);
            if (!relation.cascades(CascadeType.PERSIST)) {
                if (transientTarget) {
                    throw new MicroOrmException("Not-null property references a transient value - "
                            + "transient instance must be saved before current operation: "
                            + model.entityClass().getName() + "." + relation.javaField().getName());
                }
                continue;
            }
            if (transientTarget) {
                persist(session, attached, inProgress, inserted, deferred);
                continue;
            }
            if (!session.existsByPrimaryKey(attached, targetModel)) {
                persist(session, attached, inProgress, inserted, deferred);
            }
        }
    }

    /**
     * Hibernate {@code cascadeAfterSave}: persist collections after the owner exists
     * ({@code CascadePoint.AFTER_INSERT_BEFORE_DELETE}).
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/AbstractSaveEventListener.java#L502">AbstractSaveEventListener.cascadeAfterSave</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/internal/CascadePoint.java#L17">CascadePoint.AFTER_INSERT_BEFORE_DELETE</a>
     */
    private static void cascadePersistCollections(
            RelationPersistSession session,
            Object owner,
            EntityModel ownerModel,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        for (OneToManyField relation : ownerModel.oneToManyRelations()) {
            if (!relation.cascades(CascadeType.PERSIST)) {
                continue;
            }
            EntityCollection<?> collection = EntityCollection.get(relation, owner);
            if (collection == null || !collection.isMaterialized()) {
                continue;
            }
            EntityModel childModel = session.registry().get(relation.targetEntityClass());
            ManyToOneField inverse = childModel.manyToOneByFieldName(relation.mappedBy());
            for (Object child : collection.elementsOrEmpty()) {
                setRefToOwner(inverse, child, owner);
                if (inserted.contains(child) || inProgress.contains(child)) {
                    continue;
                }
                persist(session, child, inProgress, inserted, deferred);
            }
        }
        persistManyToMany(session, owner, ownerModel, inProgress, inserted, deferred);
    }

    /**
     * Hibernate collection persist after the owner exists, then recreate join-table rows
     * on the owning side ({@code AbstractCollectionPersister}).
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/persister/collection/AbstractCollectionPersister.java">AbstractCollectionPersister</a>
     */
    private static void persistManyToMany(
            RelationPersistSession session,
            Object owner,
            EntityModel ownerModel,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        for (ManyToManyField relation : ownerModel.manyToManyRelations()) {
            EntityCollection<?> collection = EntityCollection.get(relation, owner);
            if (collection == null || !collection.isMaterialized()) {
                continue;
            }
            EntityModel childModel = session.registry().get(relation.targetEntityClass());
            Set<Object> targetPks = new HashSet<>();
            for (Object child : collection.elementsOrEmpty()) {
                boolean transientTarget = RelationValues.isNew(child, childModel);
                if (relation.cascades(CascadeType.PERSIST)) {
                    if (!inserted.contains(child) && !inProgress.contains(child)
                            && (transientTarget || !session.existsByPrimaryKey(child, childModel))) {
                        persist(session, child, inProgress, inserted, deferred);
                    }
                } else if (transientTarget) {
                    throw new MicroOrmException("Not-null property references a transient value - "
                            + "transient instance must be saved before current operation: "
                            + ownerModel.entityClass().getName() + "." + relation.javaField().getName());
                }
                Object childPk = session.pkValue(child, childModel);
                if (childPk == null) {
                    throw new MicroOrmException("Not-null property references a transient value - "
                            + "transient instance must be saved before current operation: "
                            + ownerModel.entityClass().getName() + "." + relation.javaField().getName());
                }
                targetPks.add(childPk);
            }
            if (relation.owning()) {
                session.replaceJoinTableLinks(relation, session.pkValue(owner, ownerModel), targetPks);
            }
        }
    }

    /**
     * Hibernate {@code cascadeOnMerge} ({@code CascadePoint.BEFORE_MERGE}): many-to-one then collections.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultMergeEventListener.java#L618">DefaultMergeEventListener.cascadeOnMerge</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/internal/CascadePoint.java#L62">CascadePoint.BEFORE_MERGE</a>
     */
    private static void mergeAssociations(
            RelationPersistSession session,
            Object entity,
            EntityModel model,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        for (ManyToOneField relation : model.manyToOneRelations()) {
            if (!relation.cascades(CascadeType.MERGE)) {
                continue;
            }
            EntityRef<?> ref = EntityRef.get(relation, entity);
            Object attached = ref == null ? null : ref.attachedEntity();
            if (attached == null || inserted.contains(attached)) {
                continue;
            }
            merge(session, attached, inProgress, inserted, deferred);
        }
        for (OneToManyField relation : model.oneToManyRelations()) {
            boolean mergeChildren = relation.cascades(CascadeType.MERGE);
            if (mergeChildren || relation.orphanRemoval()) {
                syncMergeCollection(
                        session,
                        entity,
                        model,
                        relation,
                        inProgress,
                        inserted,
                        deferred,
                        relation.orphanRemoval(),
                        mergeChildren);
            }
        }
        mergeManyToMany(session, entity, model, inProgress, inserted, deferred);
    }

    /**
     * Hibernate merge of a {@code @ManyToMany}: cascade elements, then recreate join rows on the owning side.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultMergeEventListener.java#L618">DefaultMergeEventListener.cascadeOnMerge</a>
     */
    private static void mergeManyToMany(
            RelationPersistSession session,
            Object entity,
            EntityModel model,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        for (ManyToManyField relation : model.manyToManyRelations()) {
            EntityCollection<?> collection = EntityCollection.get(relation, entity);
            if (collection == null || !collection.isMaterialized()) {
                continue;
            }
            EntityModel childModel = session.registry().get(relation.targetEntityClass());
            boolean mergeChildren = relation.cascades(CascadeType.MERGE);
            Set<Object> targetPks = new HashSet<>();
            for (Object child : collection.elementsOrEmpty()) {
                if (mergeChildren) {
                    merge(session, child, inProgress, inserted, deferred);
                } else if (RelationValues.isNew(child, childModel)) {
                    throw new MicroOrmException("Not-null property references a transient value - "
                            + "transient instance must be saved before current operation: "
                            + model.entityClass().getName() + "." + relation.javaField().getName());
                }
                Object childPk = session.pkValue(child, childModel);
                if (childPk != null) {
                    targetPks.add(childPk);
                }
            }
            if (relation.owning()) {
                session.replaceJoinTableLinks(relation, session.pkValue(entity, model), targetPks);
            }
        }
    }

    /**
     * Hibernate collection remove: delete join-table rows before the owner.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultDeleteEventListener.java#L491">DefaultDeleteEventListener.cascadeBeforeDelete</a>
     */
    private static void unlinkManyToMany(
            RelationPersistSession session,
            Object entity,
            EntityModel model,
            Object ownerPk) {
        for (ManyToManyField relation : model.manyToManyRelations()) {
            ManyToManyField owning = relation.owningSide(session.registry());
            if (relation.owning()) {
                session.deleteJoinTableLinks(owning, ownerPk);
            } else {
                session.deleteJoinTableLinksByTarget(owning, ownerPk);
            }
        }
    }

    private static List<Object> collectManyToManyRemoveTargets(
            RelationPersistSession session,
            Object entity,
            EntityModel model) {
        List<Object> targets = new ArrayList<>();
        for (ManyToManyField relation : model.manyToManyRelations()) {
            if (!relation.cascades(CascadeType.REMOVE)) {
                continue;
            }
            EntityCollection<?> collection = EntityCollection.get(relation, entity);
            if (collection == null) {
                continue;
            }
            targets.addAll(collection.get());
        }
        return targets;
    }

    /**
     * Hibernate merge of an associated instance: transient or missing row → persist, else update.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultMergeEventListener.java#L273">DefaultMergeEventListener.entityIsTransient</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultMergeEventListener.java#L257">entityIsPersistent</a>
     */
    private static void merge(
            RelationPersistSession session,
            Object entity,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        if (inserted.contains(entity)) {
            return;
        }
        EntityModel model = session.registry().get(entity.getClass());
        if (RelationValues.isNew(entity, model) || !session.existsByPrimaryKey(entity, model)) {
            persist(session, entity, inProgress, inserted, deferred);
            return;
        }
        if (!inProgress.add(entity)) {
            return;
        }
        session.updateEntityRow(entity, model, deferred);
        inserted.add(entity);
        mergeAssociations(session, entity, model, inProgress, inserted, deferred);
    }

    /**
     * Hibernate collection merge plus {@code Cascade.deleteOrphans} when {@code orphanRemoval} is set.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/internal/Cascade.java#L630">Cascade.deleteOrphans</a>
     */
    private static void syncMergeCollection(
            RelationPersistSession session,
            Object owner,
            EntityModel ownerModel,
            OneToManyField relation,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred,
            boolean orphanRemoval,
            boolean mergeChildren) {
        EntityCollection<?> collection = EntityCollection.get(relation, owner);
        if (collection == null || !collection.isMaterialized()) {
            return;
        }
        EntityModel childModel = session.registry().get(relation.targetEntityClass());
        ManyToOneField inverse = childModel.manyToOneByFieldName(relation.mappedBy());
        Set<Object> desiredChildPks = new HashSet<>();
        for (Object child : collection.elementsOrEmpty()) {
            setRefToOwner(inverse, child, owner);
            if (mergeChildren) {
                merge(session, child, inProgress, inserted, deferred);
            }
            Object childPk = session.pkValue(child, childModel);
            if (childPk != null) {
                desiredChildPks.add(childPk);
            }
        }
        if (orphanRemoval && !RelationValues.isNew(owner, ownerModel)) {
            session.deleteOrphanChildren(relation, session.pkValue(owner, ownerModel), desiredChildPks, childModel);
        }
    }

    /**
     * Second pass for cyclic graphs: write a nullable FK after both rows exist
     * (Hibernate unresolved entity-insert actions).
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/spi/ActionQueue.java#L267">ActionQueue unresolved entity inserts</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/spi/ActionQueue.java#L453">checkNoUnresolvedActionsAfterOperation</a>
     */
    private static void applyDeferredFkUpdates(RelationPersistSession session, List<DeferredFkUpdate> deferred) {
        for (DeferredFkUpdate update : deferred) {
            session.updateJoinColumn(update.entity(), update.model(), update.relation());
        }
    }

    public record DeferredFkUpdate(Object entity, EntityModel model, ManyToOneField relation) {
    }

    private static void setRefToOwner(ManyToOneField inverse, Object child, Object owner) {
        if (EagerRef.class.isAssignableFrom(inverse.javaField().getType())) {
            EntityRef.set(inverse, child, EagerRef.to(owner));
        } else {
            EntityRef.set(inverse, child, LazyRef.to(owner));
        }
    }
}
