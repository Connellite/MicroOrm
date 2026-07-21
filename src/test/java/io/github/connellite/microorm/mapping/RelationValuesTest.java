package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.relation.LazyRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelationValuesTest {

    @Entity
    @Table(name = "relation_value_targets")
    static class Target {
        @Id
        Long id;
    }

    @Entity
    @Table(name = "relation_value_owners")
    static class Owner {
        @Id
        long id;

        @ManyToOne
        @JoinColumn(name = "target_id")
        LazyRef<Target> nullableTarget;

        @ManyToOne
        @JoinColumn(name = "required_target_id", nullable = false)
        LazyRef<Target> requiredTarget;
    }

    private final EntityModelRegistry registry = new EntityModelRegistry();
    private final EntityModel ownerModel;

    RelationValuesTest() {
        registry.register(Target.class);
        ownerModel = registry.register(Owner.class);
    }

    @Test
    void resolvesExplicitForeignKey() {
        Owner owner = new Owner();
        ManyToOneField relation = ownerModel.manyToOneByFieldName("nullableTarget");
        owner.nullableTarget = LazyRef.toId(Target.class, 42L);

        Object value = RelationValues.joinColumnValue(owner, ownerModel, relation, registry, SqliteDialect.getInstance());

        assertEquals(42L, value);
    }

    @Test
    void resolvesAttachedEntityPrimaryKey() {
        Owner owner = new Owner();
        Target target = new Target();
        target.id = 7L;
        ManyToOneField relation = ownerModel.manyToOneByFieldName("nullableTarget");
        owner.nullableTarget = LazyRef.to(target);

        Object value = RelationValues.joinColumnValue(owner, ownerModel, relation, registry, SqliteDialect.getInstance());

        assertEquals(7L, value);
    }

    @Test
    void nullableMissingReferenceReturnsNull() {
        Owner owner = new Owner();
        ManyToOneField relation = ownerModel.manyToOneByFieldName("nullableTarget");

        assertNull(RelationValues.joinColumnValue(owner, ownerModel, relation, registry, SqliteDialect.getInstance()));
    }

    @Test
    void requiredMissingReferenceThrows() {
        Owner owner = new Owner();
        ManyToOneField relation = ownerModel.manyToOneByFieldName("requiredTarget");

        assertThrows(MicroOrmException.class,
                () -> RelationValues.joinColumnValue(owner, ownerModel, relation, registry, SqliteDialect.getInstance()));
    }

    @Test
    void requiredUnresolvedReferenceCanBeDeferred() {
        Owner owner = new Owner();
        Target target = new Target();
        ManyToOneField relation = ownerModel.manyToOneByFieldName("requiredTarget");
        owner.requiredTarget = LazyRef.to(target);

        Object value = RelationValues.joinColumnValue(
                owner, ownerModel, relation, registry, SqliteDialect.getInstance(), true);

        assertNull(value);
    }

    @Test
    void requiredUnresolvedReferenceThrowsWhenNotDeferred() {
        Owner owner = new Owner();
        Target target = new Target();
        ManyToOneField relation = ownerModel.manyToOneByFieldName("requiredTarget");
        owner.requiredTarget = LazyRef.to(target);

        assertThrows(MicroOrmException.class,
                () -> RelationValues.joinColumnValue(owner, ownerModel, relation, registry, SqliteDialect.getInstance()));
    }
}
