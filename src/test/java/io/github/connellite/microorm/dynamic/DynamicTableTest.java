package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.exception.MicroOrmException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicTableTest {

    @Test
    void builderCreatesTableWithPrimaryKey() {
        DynamicTable table = DynamicTable.builder("orders")
                .table("order_items")
                .column("id", LogicalType.UUID, c -> c.primaryKey().notNull())
                .column("name", LogicalType.STRING, Column.Builder::notNull)
                .column("qty", LogicalType.INT)
                .build();

        assertEquals("orders", table.name());
        assertEquals("order_items", table.tableName());
        assertEquals(3, table.columns().size());
        assertEquals("id", table.primaryKey().name());
    }

    @Test
    void rejectsTableWithoutPrimaryKey() {
        assertThrows(IllegalArgumentException.class, () -> DynamicTable.builder("bad")
                .column("name", LogicalType.STRING, null)
                .build());
    }

    @Test
    void rejectsMultiplePrimaryKeys() {
        assertThrows(IllegalArgumentException.class, () -> DynamicTable.builder("bad")
                .column("a", LogicalType.UUID, Column.Builder::primaryKey)
                .column("b", LogicalType.UUID, Column.Builder::primaryKey)
                .build());
    }

    @Test
    void registryReturnsRegisteredTable() {
        DynamicTableRegistry registry = new DynamicTableRegistry();
        DynamicTable table = DynamicTable.builder("items")
                .column("id", LogicalType.LONG, c -> c.primaryKey().autoIncrement())
                .build();
        registry.register(table);

        assertTrue(registry.isRegistered("items"));
        assertEquals(table, registry.get("items"));
    }

    @Test
    void registryThrowsForUnknownTable() {
        DynamicTableRegistry registry = new DynamicTableRegistry();
        assertThrows(MicroOrmException.class, () -> registry.get("missing"));
    }
}
