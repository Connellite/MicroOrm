package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.session.RelationPersistSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Persists entity graphs with {@link LazyRef} / {@link LazyCollection}.
 * <p>
 * Flush order follows Hibernate's {@code ActionQueue} (inserts, then updates, then collection sync, then deletes)
 * so foreign-key constraints stay valid without disabling them:
 * <a href="https://github.com/hibernate/hibernate-orm/blob/main/hibernate-core/src/main/java/org/hibernate/action/internal/AbstractEntityInsertAction.java">
 * Hibernate ORM insert/update ordering</a>,
 * <a href="https://vladmihalcea.com/the-best-way-to-map-a-onetomany-association-with-jpa-and-hibernate/">
 * Vlad Mihalcea — propagate FK via the {@code @ManyToOne} owning side</a>.
 * <p>
 * Cyclic object graphs (e.g. document ↔ files) are handled with a two-pass insert: rows are inserted with
 * nullable FKs left {@code NULL} when the target has no primary key yet, then deferred {@code UPDATE}s
 * assign the FK once both sides exist (same approach as Hibernate with deferrable / two-phase flush).
 */
public final class RelationPersister {

    private RelationPersister() {
    }

    public static <T> T insert(RelationPersistSession session, T entity) {
        List<DeferredFkUpdate> deferred = new ArrayList<>();
        Set<Object> inProgress = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> inserted = Collections.newSetFromMap(new IdentityHashMap<>());
        persistInsert(session, entity, inProgress, inserted, deferred);
        applyDeferredFkUpdates(session, deferred);
        return entity;
    }

    public static int update(RelationPersistSession session, Object entity) {
        EntityModel model = session.registry().get(entity.getClass());
        int rows = session.updateEntityRow(entity, model, List.of());
        List<DeferredFkUpdate> deferred = new ArrayList<>();
        Set<Object> inProgress = Collections.newSetFromMap(new IdentityHashMap<>());
        syncOneToManyCollections(session, entity, model, inProgress, Collections.newSetFromMap(new IdentityHashMap<>()), deferred, true);
        applyDeferredFkUpdates(session, deferred);
        return rows;
    }

    public static int delete(RelationPersistSession session, Object entity) {
        EntityModel model = session.registry().get(entity.getClass());
        session.requirePkSet(entity, model);
        Object ownerPk = session.pkValue(entity, model);
        for (OneToManyField relation : model.oneToManyRelations()) {
            session.deleteChildrenByOwner(relation, ownerPk);
        }
        return session.deleteEntityRow(entity, model);
    }

    private static void persistInsert(
            RelationPersistSession session,
            Object entity,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred) {
        if (!inProgress.add(entity)) {
            return;
        }
        EntityModel model = session.registry().get(entity.getClass());

        for (ManyToOneField relation : model.manyToOneRelations()) {
            LazyRef<?> ref = LazyRef.get(relation, entity);
            if (ref == null) {
                continue;
            }
            Object attached = ref.attachedEntity();
            if (attached == null || inserted.contains(attached)) {
                continue;
            }
            EntityModel targetModel = session.registry().get(relation.targetEntityClass());
            if (!RelationValues.isNew(attached, targetModel)) {
                continue;
            }
            persistInsert(session, attached, inProgress, inserted, deferred);
        }

        if (!inserted.contains(entity)) {
            session.assignGeneratedUuidIfNeeded(entity, model);
            session.insertEntityRow(entity, model, deferred);
            inserted.add(entity);
        }

        syncOneToManyCollections(session, entity, model, inProgress, inserted, deferred, false);
    }

    private static void syncOneToManyCollections(
            RelationPersistSession session,
            Object owner,
            EntityModel ownerModel,
            Set<Object> inProgress,
            Set<Object> inserted,
            List<DeferredFkUpdate> deferred,
            boolean reconcileOrphans) {
        Object ownerPk = session.pkValue(owner, ownerModel);

        for (OneToManyField relation : ownerModel.oneToManyRelations()) {
            LazyCollection<?> collection = LazyCollection.get(relation, owner);
            if (collection == null || !collection.isMaterialized()) {
                continue;
            }
            EntityModel childModel = session.registry().get(relation.targetEntityClass());
            ManyToOneField inverse = childModel.manyToOneByFieldName(relation.mappedBy());
            Set<Object> desiredChildPks = new HashSet<>();
            for (Object child : collection.elementsOrEmpty()) {
                LazyRef.set(inverse, child, LazyRef.to(owner));
                if (!inserted.contains(child)) {
                    persistInsert(session, child, inProgress, inserted, deferred);
                } else {
                    session.updateEntityRow(child, childModel, deferred);
                }
                desiredChildPks.add(session.pkValue(child, childModel));
            }
            if (reconcileOrphans && !RelationValues.isNew(owner, ownerModel)) {
                session.deleteOrphanChildren(relation, ownerPk, desiredChildPks, childModel);
            }
        }
    }

    private static void applyDeferredFkUpdates(RelationPersistSession session, List<DeferredFkUpdate> deferred) {
        for (DeferredFkUpdate update : deferred) {
            session.updateJoinColumn(update.entity(), update.model(), update.relation());
        }
    }

    public record DeferredFkUpdate(Object entity, EntityModel model, ManyToOneField relation) {
    }
}
