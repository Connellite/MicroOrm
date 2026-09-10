package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.annotation.UuidGenerator;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.generation.IdGenerationKind;
import io.github.connellite.microorm.type.AttributeConverter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicTableTest {

    public record Money(String currency, BigDecimal amount) {
    }

    public static class MoneyConverter implements AttributeConverter<Money, String> {
        @Override
        public String convertToDatabaseColumn(Money attribute) {
            return attribute == null ? null : attribute.currency() + ":" + attribute.amount();
        }

        @Override
        public Money convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            String[] parts = dbData.split(":", 2);
            return new Money(parts[0], new BigDecimal(parts[1]));
        }
    }

    public static class WrongDatabaseConverter implements AttributeConverter<Money, Integer> {
        @Override
        public Integer convertToDatabaseColumn(Money attribute) {
            return 1;
        }

        @Override
        public Money convertToEntityAttribute(Integer dbData) {
            return null;
        }
    }

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
    void builderCreatesColumnWithConverter() {
        DynamicTable table = DynamicTable.builder("orders")
                .column("id", LogicalType.UUID, Column.Builder::primaryKey)
                .column("total", LogicalType.STRING, c -> c.converter(MoneyConverter.class))
                .build();

        Column total = table.columnByName("total");
        assertTrue(total.converted());
        assertEquals(Money.class, total.javaType());
        assertEquals(String.class, total.jdbcJavaType());
        assertEquals("USD:12.34", total.convertToDatabaseColumn(new Money("USD", new BigDecimal("12.34"))));
        assertEquals(new Money("USD", new BigDecimal("12.34")), total.convertToEntityAttribute("USD:12.34"));
    }

    @Test
    void rejectsConverterWhoseDatabaseTypeDoesNotMatchLogicalType() {
        assertThrows(MicroOrmException.class, () -> DynamicTable.builder("bad")
                .column("id", LogicalType.UUID, Column.Builder::primaryKey)
                .column("total", LogicalType.STRING, c -> c.converter(WrongDatabaseConverter.class))
                .build());
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
                .column("id", LogicalType.LONG, c -> c.primaryKey().generatedValue(GenerationType.IDENTITY))
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

    @Test
    void generatedValueIdentityStoresGenerationMetadata() {
        DynamicTable table = DynamicTable.builder("items")
                .column("id", LogicalType.LONG, c -> c.primaryKey().generatedValue(GenerationType.IDENTITY))
                .build();

        assertEquals(IdGenerationKind.IDENTITY, table.primaryKey().idGeneration().kind());
        assertTrue(table.primaryKey().autoIncrement());
    }

    @Test
    void generatedValueSequenceStoresGenerationMetadata() {
        DynamicTable table = DynamicTable.builder("items")
                .column("id", LogicalType.LONG, c -> c.primaryKey()
                        .sequenceGenerator("item_seq", g -> g.sequenceName("items_seq").allocationSize(5).initialValue(10))
                        .generatedValue(GenerationType.SEQUENCE, "item_seq"))
                .build();

        assertEquals(IdGenerationKind.SEQUENCE, table.primaryKey().idGeneration().kind());
        assertEquals("item_seq", table.primaryKey().idGeneration().generatorName());
        assertEquals("items_seq", table.primaryKey().idGeneration().sequenceName());
        assertEquals(5, table.primaryKey().idGeneration().allocationSize());
        assertEquals(10, table.primaryKey().idGeneration().initialValue());
    }

    @Test
    void genericNativeGeneratorStoresIdentityGenerationMetadata() {
        DynamicTable table = DynamicTable.builder("items")
                .column("id", LogicalType.LONG, c -> c.primaryKey()
                        .genericGenerator("native_generator", "native")
                        .generatedValue("native_generator"))
                .build();

        assertEquals(IdGenerationKind.IDENTITY, table.primaryKey().idGeneration().kind());
        assertEquals("native_generator", table.primaryKey().idGeneration().generatorName());
    }

    @Test
    void uuidGeneratorStoresUuidGenerationMetadata() {
        DynamicTable table = DynamicTable.builder("items")
                .column("id", LogicalType.UUID, c -> c.primaryKey()
                        .uuidGenerator(UuidGenerator.Version.VERSION_7))
                .build();

        assertEquals(IdGenerationKind.UUID, table.primaryKey().idGeneration().kind());
        assertEquals(7, table.primaryKey().idGeneration().uuidVersion());
    }

    @Test
    void rejectsGeneratedValueOnNonPrimaryKey() {
        assertThrows(IllegalArgumentException.class, () -> DynamicTable.builder("bad")
                .column("id", LogicalType.LONG, c -> c.generatedValue(GenerationType.IDENTITY))
                .build());
    }

    @Test
    void rejectsGeneratedValueOnUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> DynamicTable.builder("bad")
                .column("id", LogicalType.UUID, c -> c.primaryKey().generatedValue(GenerationType.IDENTITY))
                .build());
        assertThrows(IllegalArgumentException.class, () -> DynamicTable.builder("bad")
                .column("id", LogicalType.STRING, c -> c.primaryKey().generatedValue(GenerationType.IDENTITY))
                .build());
    }

    @Test
    void rejectsUuidGeneratorOnUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> DynamicTable.builder("bad")
                .column("id", LogicalType.LONG, c -> c.primaryKey().uuidGenerator())
                .build());
    }

    @Test
    void rejectsUuidGeneratorCombinedWithGeneratedValue() {
        assertThrows(IllegalArgumentException.class, () -> DynamicTable.builder("bad")
                .column("id", LogicalType.UUID, c -> c.primaryKey()
                        .uuidGenerator()
                        .generatedValue(GenerationType.IDENTITY))
                .build());
    }

    @Test
    void rejectsUnsupportedGenericGenerator() {
        assertThrows(IllegalArgumentException.class, () -> DynamicTable.builder("bad")
                .column("id", LogicalType.LONG, c -> c.primaryKey()
                        .genericGenerator("custom", "uuid2")
                        .generatedValue("custom"))
                .build());
    }
}
