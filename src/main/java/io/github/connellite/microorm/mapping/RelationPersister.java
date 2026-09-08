package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.annotation.CascadeType;
import io.github.connellite.microorm.relation.EagerRef;
import io.github.connellite.microorm.relation.EntityCollection;
import io.github.connellite.microorm.relation.EntityRef;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.session.RelationPersistSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persist / merge / remove for relation graphs, following Hibernate's cascade points
 * and ActionQueue flush order so foreign-key constraints stay valid:
 * <ul>
 *   <li>{@code persist}: cascade many-to-one before insert (parents first), then insert,
 *       then cascade collections (children after the FK owner exists)</li>
 *   <li>nullable FKs to a not-yet-inserted target are left {@code NULL} and updated after
 *       both rows exist (Hibernate {@code ForeignKeys.Nullifier} / two-pass cycle handling)</li>
 *   <li>required ({@code optional=false}) FKs to a transient target fail like Hibernate
 *       {@code ForeignKeys.findNonNullableTransientEntities} /
 *       {@code TransientPropertyValueException}; optional/nullable transients are nullified</li>
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
        Map<OneToOneField, Object> previousOwningFks = snapshotOwningOneToOneForeignKeys(session, entity, model);
        List<DeferredFkUpdate> deferred = new ArrayList<>();
        Set<Object> inProgress = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> inserted = Collections.newSetFromMap(new IdentityHashMap<>());
        inProgress.add(entity);
        mergeOwningToOnes(session, entity, model, inProgress, inserted, deferred);
        int rows = session.updateEntityRow(entity, model, deferred);
        inserted.add(entity);
        mergeAssociations(session, entity, model, inProgress, inserted, deferred, previousOwningFks);
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
        for (OneToOneField relation : model.oneToOneRelations()) {
            if (relation.owning()) {
                continue;
            }
            if (relation.cascades(CascadeType.REMOVE) || relation.orphanRemoval()) {
                session.deleteInverseOneToOne(relation, ownerPk);
            }
        }
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
        for (OneToOneField relation : model.oneToOneRelations()) {
            if (!relation.owning() || !relation.orphanRemoval() || relation.cascades(CascadeType.REMOVE)) {
                continue;
            }
            ManyToOneField join = model.manyToOneByFieldName(relation.javaField().getName());
            EntityRef<?> ref = EntityRef.get(join, entity);
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
        validatePersistManyToManyTargets(session, entity, model, inserted);
        session.assignGeneratedIdsIfNeeded(entity, model);
        session.insertEntityRow(entity, model, deferred, inserted, inProgress);
        inserted.add(entity);
        cascadePersistCollections(session, entity, model, inProgress, inserted, deferred);
    }

    /**
     * Hibernate {@code cascadeBeforeSave}: persist many-to-one before the owner insert
     * ({@code CascadePoint.BEFORE_INSERT_AFTER_DELETE}). Uncascaded transients are not rejected
     * here — Hibernate's cascade visitor only persists when {@code CascadeType.PERSIST} is set.
     * A <em>required</em> ({@code optional=false} / {@code JoinColumn.nullable=false}) transient
     * target fails later like {@code ForeignKeys.findNonNullableTransientEntities} /
     * {@code TransientPropertyValueException}. A nullable one is nullified on insert.
     *
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/event/internal/AbstractSaveEventListener.java#L471">AbstractSaveEventListener.cascadeBeforeSave</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/internal/CascadePoint.java#L23">CascadePoint.BEFORE_INSERT_AFTER_DELETE</a>
     * @see <a href="https://github.com/hibernate/hibernate-orm/blob/7.4.7/hibernate-core/src/main/java/org/hibernate/engine/internal/ForeignKeys.java">ForeignKeys.findNonNullableTransientEntities</a>
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
            if (!relation.cascades(CascadeType.PERSIST)) {
                continue;
            }
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
            if (transientTarget || !session.existsByPrimaryKey(attached, targetModel)) {
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
        persistInverseOneToOnes(session, owner, ownerModel, inProgress, inserted, deferred);
    }

    private static void validatePersistManyToManyTargets(
            RelationPersistSession session,
            Object owner,
            EntityModel ownerModel,
            Set<Object> inserted) {
        for (ManyToManyField relation : ownerModel.manyToManyRelations()) {
            if (!relation.owning()) {
                continue;
            }
            if (relation.cascades(CascadeType.PERSIST)) {
                continue;
            }
            EntityCollection<?> collection = EntityCollection.get(relation, owner);
            if (collection == null || !collection.isMaterialized()) {
                continue;
            }
            EntityModel childModel = session.registry().get(relation.targetEntityClass());
            for (Object child : collection.elementsOrEmpty()) {
                if (RelationValues.isNew(child, childModel)) {
                    throw RelationValues.requiredTransientAssociation(ownerModel.entityClass(), relation.javaField().getName());
                }
                if (!inserted.contains(child) && !session.existsByPrimaryKey(child, childModel)) {
                    throw RelationValues.requiredTransientAssociation(ownerModel.entityClass(), relation.javaField().getName());
                }
            }
        }
    }

    /**
     * Inverse {@code @OneToOne} is persisted after the owner exists so the owning join column
     * can point at the owner primary key.
     */
    private static void persistInverseOneToOnes(
            RelationPersistSession session,
            Object owner,
            EntityModel ownerModel,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        for (OneToOneField relation : ownerModel.oneToOneRelations()) {
            if (relation.owning()) {
                continue;
            }
            EntityRef<?> ref = EntityRef.get(relation, owner);
            if (ref == null) {
                continue;
            }
            Object child = ref.attachedEntity();
            if (child == null) {
                continue;
            }
            EntityModel childModel = session.registry().get(relation.targetEntityClass());
            ManyToOneField owning = childModel.manyToOneByFieldName(relation.mappedBy());
            setRefToOwner(owning, child, owner);
            boolean transientTarget = RelationValues.isNew(child, childModel);
            if (relation.cascades(CascadeType.PERSIST)) {
                if (!inserted.contains(child) && !inProgress.contains(child)
                        && (transientTarget || !session.existsByPrimaryKey(child, childModel))) {
                    persist(session, child, inProgress, inserted, deferred);
                } else if (!transientTarget) {
                    session.updateJoinColumn(child, childModel, owning);
                }
            }
            // Unowned / inverse without cascade: Hibernate skips persist (CascadeStyles.NONE).
            // CHECK_ON_FLUSH also skips unowned associations unless unowned_association_transient_check.
        }
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
            if (!relation.owning() && !relation.cascades(CascadeType.PERSIST)) {
                continue;
            }
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
                    throw RelationValues.requiredTransientAssociation(
                            ownerModel.entityClass(), relation.javaField().getName());
                } else if (!inserted.contains(child) && !session.existsByPrimaryKey(child, childModel)) {
                    throw RelationValues.requiredTransientAssociation(ownerModel.entityClass(), relation.javaField().getName());
                }
                Object childPk = session.pkValue(child, childModel);
                if (childPk == null) {
                    throw RelationValues.requiredTransientAssociation(
                            ownerModel.entityClass(), relation.javaField().getName());
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
        mergeAssociations(session, entity, model, inProgress, inserted, deferred, Map.of());
    }

    private static void mergeAssociations(
            RelationPersistSession session,
            Object entity,
            EntityModel model,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred,
            Map<OneToOneField, Object> previousOwningFks) {
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
        mergeInverseOneToOnes(session, entity, model, inProgress, inserted, deferred);
        deleteOwningOneToOneOrphans(session, entity, model, previousOwningFks);
    }

    private static void mergeInverseOneToOnes(
            RelationPersistSession session,
            Object entity,
            EntityModel model,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        for (OneToOneField relation : model.oneToOneRelations()) {
            if (relation.owning()) {
                continue;
            }
            boolean mergeChild = relation.cascades(CascadeType.MERGE);
            if (!mergeChild && !relation.orphanRemoval()) {
                continue;
            }
            EntityRef<?> ref = EntityRef.get(relation, entity);
            Object attached = ref == null ? null : ref.attachedEntity();
            EntityModel childModel = session.registry().get(relation.targetEntityClass());
            ManyToOneField owning = childModel.manyToOneByFieldName(relation.mappedBy());
            Set<Object> retained = new HashSet<>();
            if (attached != null && !RelationValues.isNew(attached, childModel)) {
                Object childPk = session.pkValue(attached, childModel);
                if (childPk != null) {
                    retained.add(childPk);
                }
            }
            if (relation.orphanRemoval() && !RelationValues.isNew(entity, model)) {
                session.deleteOrphanInverseOneToOne(
                        relation, session.pkValue(entity, model), retained, childModel);
            }
            if (attached == null) {
                continue;
            }
            setRefToOwner(owning, attached, entity);
            if (mergeChild) {
                merge(session, attached, inProgress, inserted, deferred);
            } else if (!RelationValues.isNew(attached, childModel)) {
                session.updateJoinColumn(attached, childModel, owning);
            }
            // Inverse without cascade MERGE: do not persist a transient child (Hibernate cascade NONE).
        }
    }

    /**
     * Merge owning to-one targets before the owner row update so a new target primary key
     * is available for the join column.
     */
    private static void mergeOwningToOnes(
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
    }

    private static Map<OneToOneField, Object> snapshotOwningOneToOneForeignKeys(
            RelationPersistSession session,
            Object entity,
            EntityModel model) {
        if (RelationValues.isNew(entity, model)) {
            return Map.of();
        }
        Object ownerPk = session.pkValue(entity, model);
        Map<OneToOneField, Object> previous = new LinkedHashMap<>();
        for (OneToOneField relation : model.oneToOneRelations()) {
            if (!relation.owning() || !relation.orphanRemoval()) {
                continue;
            }
            ManyToOneField join = model.manyToOneByFieldName(relation.javaField().getName());
            previous.put(relation, session.currentForeignKey(model, join, ownerPk));
        }
        return previous;
    }

    private static void deleteOwningOneToOneOrphans(
            RelationPersistSession session,
            Object entity,
            EntityModel model,
            Map<OneToOneField, Object> previousOwningFks) {
        if (previousOwningFks == null || previousOwningFks.isEmpty()) {
            return;
        }
        for (Map.Entry<OneToOneField, Object> entry : previousOwningFks.entrySet()) {
            Object previousFk = entry.getValue();
            if (previousFk == null) {
                continue;
            }
            OneToOneField relation = entry.getKey();
            ManyToOneField join = model.manyToOneByFieldName(relation.javaField().getName());
            EntityRef<?> ref = EntityRef.get(join, entity);
            Object currentFk = ref == null ? null : RelationValues.resolveRawForeignKey(ref, join, session.registry());
            if (previousFk.equals(currentFk)) {
                continue;
            }
            EntityModel targetModel = session.registry().get(join.targetEntityClass());
            Object previous = session.selectByPrimaryKey(join.targetEntityClass(), previousFk);
            if (previous != null) {
                if (targetModel.hasRelations()) {
                    delete(session, previous);
                } else {
                    session.deleteEntityRow(previous, targetModel);
                }
            }
        }
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
            if (!relation.owning() && !mergeChildren) {
                continue;
            }
            Set<Object> targetPks = new HashSet<>();
            for (Object child : collection.elementsOrEmpty()) {
                if (mergeChildren) {
                    merge(session, child, inProgress, inserted, deferred);
                } else if (RelationValues.isNew(child, childModel)) {
                    throw RelationValues.requiredTransientAssociation(
                            model.entityClass(), relation.javaField().getName());
                } else if (!inserted.contains(child) && !session.existsByPrimaryKey(child, childModel)) {
                    throw RelationValues.requiredTransientAssociation(model.entityClass(), relation.javaField().getName());
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
        Map<OneToOneField, Object> previousOwningFks = snapshotOwningOneToOneForeignKeys(session, entity, model);
        mergeOwningToOnes(session, entity, model, inProgress, inserted, deferred);
        session.updateEntityRow(entity, model, deferred);
        inserted.add(entity);
        mergeAssociations(session, entity, model, inProgress, inserted, deferred, previousOwningFks);
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
