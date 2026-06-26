package io.github.connellite.microorm.jdbc;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityHydratorTest {

    @Entity(name = "hydrator_items")
    static class Item {
        @Id(autoIncrement = true)
        private long id;

        @Column(nullable = false)
        private String name;

        @Column(nullable = false)
        private int count;
    }

    private EntityModel model;
    private EntityField pk;
    private EntityField countField;

    @BeforeEach
    void setUp() {
        model = new EntityModelRegistry().register(Item.class);
        pk = model.primaryKey();
        countField = model.fields().stream()
                .filter(f -> "count".equals(f.columnName()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void isUnsetPkTreatsNullAndZeroAsUnset() {
        Item item = new Item();
        assertTrue(EntityHydrator.isUnsetPk(item, pk));

        item.id = 0L;
        assertTrue(EntityHydrator.isUnsetPk(item, pk));

        item.id = 5L;
        assertFalse(EntityHydrator.isUnsetPk(item, pk));
    }

    @Test
    void getAndSetFieldValueRoundTrip() {
        Item item = new Item();
        EntityField nameField = model.fields().stream()
                .filter(f -> "name".equals(f.columnName()))
                .findFirst()
                .orElseThrow();

        EntityHydrator.setFieldValue(item, nameField, "alpha");
        assertEquals("alpha", EntityHydrator.getFieldValue(item, nameField));
    }

    @Test
    void setFieldValueRejectsNullForPrimitive() {
        Item item = new Item();
        MicroOrmException ex = assertThrows(MicroOrmException.class,
                () -> EntityHydrator.setFieldValue(item, countField, null));
        assertTrue(ex.getMessage().contains("primitive"));
    }

    @Test
    void newInstanceCreatesEntity() {
        Item item = EntityHydrator.newInstance(model);
        assertTrue(item instanceof Item);
    }
}
