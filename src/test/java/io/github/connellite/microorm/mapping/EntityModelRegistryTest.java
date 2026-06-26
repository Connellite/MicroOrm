package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Transient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityModelRegistryTest {

    @Entity(name = "registry_items")
    static class Item {
        @Id
        private long id;

        @Column(nullable = false, length = 64)
        private String name;

        @Transient
        private String scratch;
    }

    @Entity
    static class DefaultTable {
        @Id
        private UUID id;
    }

    @Entity(name = "dup_id")
    static class DuplicateId {
        @Id
        private long first;

        @Id
        private long second;
    }

    static class MissingEntityAnnotation {
        @Id
        private long id;
    }

    @Entity(name = "no_pk")
    static class MissingPrimaryKey {
        private String name;
    }

    @Entity(name = "bad_col")
    static class InvalidColumnName {
        @Id
        private long id;

        @Column(name = "bad-name")
        private String label;
    }

    @Entity(name = "bad-table")
    static class InvalidTableName {
        @Id
        private long id;
    }

    @Entity(name = "unsupported_type")
    static class UnsupportedFieldType {
        @Id
        private long id;

        private List<String> tags;
    }

    @Entity(name = "temporal_fields")
    static class TemporalFields {
        @Id
        private long id;

        private java.util.Date createdAt;

        private LocalDateTime updatedAt;
    }

    static class MappedSuperclass {
        @Column
        protected String inherited;
    }

    @Entity(name = "inheritance_child")
    static class InheritanceChild extends MappedSuperclass {
        @Id
        private long id;
    }

    @Entity(name = "quoted_column")
    static class QuotedColumn {
        @Id
        private long id;

        @Column(name = "`size`")
        private int size;
    }

    @Entity
    static class OrderItem {
        @Id
        private long id;

        @Column(nullable = false)
        private String firstName;
    }

    @Test
    void springPhysicalNamingStrategyMapsToSnakeCase() {
        EntityModelRegistry registry = new EntityModelRegistry(SpringPhysicalNamingStrategy.INSTANCE);
        EntityModel model = registry.register(OrderItem.class);

        assertEquals("order_item", model.tableName());
        assertEquals("first_name", model.fields().stream()
                .filter(f -> !f.id())
                .findFirst()
                .orElseThrow()
                .columnName());
    }

    @Test
    void backtickColumnNameRequestsQuotedIdentifier() {
        EntityModelRegistry registry = new EntityModelRegistry();
        EntityModel model = registry.register(QuotedColumn.class);
        EntityField sizeField = model.fields().stream()
                .filter(f -> "size".equals(f.columnName()))
                .findFirst()
                .orElseThrow();
        assertTrue(sizeField.columnIdentifier().quoted());
    }

    @Test
    void registerBuildsModelWithTableAndFields() {
        EntityModelRegistry registry = new EntityModelRegistry();
        EntityModel model = registry.register(Item.class);

        assertEquals("registry_items", model.tableName());
        assertEquals(Item.class, model.entityClass());
        assertEquals(2, model.fields().size());
        assertFalse(model.primaryKey().autoIncrement());
        assertEquals("name", model.fields().stream()
                .filter(f -> !f.id())
                .findFirst()
                .orElseThrow()
                .columnName());
    }

    @Test
    void defaultTableNameIsLowercaseSimpleName() {
        EntityModelRegistry registry = new EntityModelRegistry();
        EntityModel model = registry.register(DefaultTable.class);
        assertEquals("defaulttable", model.tableName());
    }

    @Test
    void getRequiresPriorRegistration() {
        EntityModelRegistry registry = new EntityModelRegistry();
        registry.register(Item.class);

        assertThrows(MicroOrmException.class, () -> registry.get(DefaultTable.class));
    }

    @Test
    void rejectsMissingEntityAnnotation() {
        EntityModelRegistry registry = new EntityModelRegistry();
        assertThrows(MicroOrmException.class, () -> registry.register(MissingEntityAnnotation.class));
    }

    @Test
    void rejectsMultipleIdFields() {
        EntityModelRegistry registry = new EntityModelRegistry();
        assertThrows(MicroOrmException.class, () -> registry.register(DuplicateId.class));
    }

    @Test
    void rejectsMissingPrimaryKey() {
        EntityModelRegistry registry = new EntityModelRegistry();
        assertThrows(MicroOrmException.class, () -> registry.register(MissingPrimaryKey.class));
    }

    @Test
    void rejectsInvalidColumnNames() {
        EntityModelRegistry registry = new EntityModelRegistry();
        assertThrows(MicroOrmException.class, () -> registry.register(InvalidColumnName.class));
    }

    @Test
    void rejectsInvalidTableName() {
        EntityModelRegistry registry = new EntityModelRegistry();
        assertThrows(MicroOrmException.class, () -> registry.register(InvalidTableName.class));
    }

    @Test
    void rejectsUnsupportedFieldType() {
        EntityModelRegistry registry = new EntityModelRegistry();
        assertThrows(MicroOrmException.class, () -> registry.register(UnsupportedFieldType.class));
    }

    @Test
    void acceptsTemporalFieldTypes() {
        EntityModelRegistry registry = new EntityModelRegistry();
        EntityModel model = registry.register(TemporalFields.class);
        assertEquals(3, model.fields().size());
    }

    @Test
    void rejectsInheritedMappedFields() {
        EntityModelRegistry registry = new EntityModelRegistry();
        assertThrows(MicroOrmException.class, () -> registry.register(InheritanceChild.class));
    }
}
