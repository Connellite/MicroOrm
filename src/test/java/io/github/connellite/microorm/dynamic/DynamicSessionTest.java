package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.MicroOrm;
import io.github.connellite.microorm.DialectTestSupport;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.UuidGenerator;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.microorm.type.AttributeConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.github.connellite.microorm.dynamic.DynamicSelect.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicSessionTest {

    @Entity
    @Table(name = "sidecar")
    static class Sidecar {
        @Id
        private long id;
    }

    public record Money(String currency, BigDecimal amount) {
    }

    record UserId(String value) {
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

    public static class UserIdConverter implements AttributeConverter<UserId, String> {
        @Override
        public String convertToDatabaseColumn(UserId attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public UserId convertToEntityAttribute(String dbData) {
            return dbData == null ? null : new UserId(dbData);
        }
    }

    private static MicroOrm newOrm(DialectTestSupport.DialectFixture dialect, Connection connection) {
        MicroOrm orm = dialect.createOrm(connection);
        DynamicTable table = DynamicTable.builder("mart")
                .table("datamart_docs")
                .column("UUID", LogicalType.UUID, c -> c.primaryKey().notNull())
                .column("VersionNR", LogicalType.INT, Column.Builder::notNull)
                .column("Removed", LogicalType.BOOL, Column.Builder::notNull)
                .column("customer_name", LogicalType.STRING)
                .build();
        orm.dynamicRegistry().register(table);
        return orm;
    }

    @Test
    void insertReturningIdReturnsGeneratedIdentityKey() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            orm.dynamicRegistry().register(generatedIdTable(GenerationType.IDENTITY));
            try (DynamicSession session = orm.openDynamicSession()) {
                session.createTable("generated_items");

                Object id = session.insertReturningId("generated_items", Map.of("name", "alpha"));

                assertNumberEquals(1, id);
                Map<String, Object> row = session.selectOne("generated_items", Map.of("id", id)).orElseThrow();
                assertEquals("alpha", row.get("name"));
            }
        }
    }

    @Test
    void insertReturningIdReturnsExplicitPrimaryKey() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            orm.dynamicRegistry().register(generatedIdTable(GenerationType.IDENTITY));
            try (DynamicSession session = orm.openDynamicSession()) {
                session.createTable("generated_items");

                Object id = session.insertReturningId("generated_items", Map.of("id", 42L, "name", "explicit"));

                assertEquals(42L, id);
                Map<String, Object> row = session.selectOne("generated_items", Map.of("id", id)).orElseThrow();
                assertEquals("explicit", row.get("name"));
            }
        }
    }

    @Test
    void sqliteRejectsDynamicSequenceGeneratedIds() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            orm.dynamicRegistry().register(generatedIdTable(GenerationType.SEQUENCE));
            try (DynamicSession session = orm.openDynamicSession()) {
                MicroOrmException error = assertThrows(
                        MicroOrmException.class,
                        () -> session.createTable("generated_items"));
                assertTrue(error.getMessage().contains("GenerationType.SEQUENCE"));
            }
        }
    }

    @Test
    void insertReturningIdReturnsGeneratedUuidKey() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            orm.dynamicRegistry().register(generatedUuidTable());
            try (DynamicSession session = orm.openDynamicSession()) {
                session.createTable("generated_uuid_items");

                Object id = session.insertReturningId("generated_uuid_items", Map.of("name", "uuid7"));

                assertTrue(id instanceof UUID);
                assertEquals(7, ((UUID) id).version());
                Map<String, Object> row = session.selectOne("generated_uuid_items", Map.of("id", id)).orElseThrow();
                assertEquals("uuid7", row.get("name"));
            }
        }
    }

    @Test
    void insertReturningIdReturnsAssignedStringPrimaryKey() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            orm.dynamicRegistry().register(assignedStringIdTable());
            try (DynamicSession session = orm.openDynamicSession()) {
                session.createTable("string_users");

                Object id = session.insertReturningId("string_users", Map.of("user_id", "ada", "name", "Ada"));

                assertEquals("ada", id);
                Map<String, Object> row = session.selectOne("string_users", Map.of("user_id", id)).orElseThrow();
                assertEquals("Ada", row.get("name"));
            }
        }
    }

    @Test
    void dynamicAssignedStringPrimaryKeyMustBeNonBlankBeforeInsert() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            orm.dynamicRegistry().register(assignedStringIdTable());
            try (DynamicSession session = orm.openDynamicSession()) {
                session.createTable("string_users");

                assertThrows(MicroOrmException.class,
                        () -> session.insert("string_users", Map.of("name", "Missing id")));
                assertThrows(MicroOrmException.class,
                        () -> session.insert("string_users", Map.of("user_id", "   ", "name", "Blank id")));
                assertThrows(MicroOrmException.class,
                        () -> session.insertReturningId("string_users", Map.of("name", "Missing id")));
                assertThrows(MicroOrmException.class,
                        () -> session.insertReturningId("string_users", Map.of("user_id", "   ", "name", "Blank id")));
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void convertsDynamicColumnValuesToDatabaseColumnAndBack(DialectTestSupport.DialectFixture dialect) throws SQLException {
        UUID id = UUID.randomUUID();
        Money original = new Money("USD", new BigDecimal("12.34"));
        Money updated = new Money("EUR", new BigDecimal("56.78"));
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "dynamic_converted_orders");
            MicroOrm orm = dialect.createOrm(connection);
            orm.dynamicRegistry().register(convertedOrderTable());
            try (DynamicSession session = orm.openDynamicSession()) {
                session.createTable("converted_orders");

                session.insert("converted_orders", Map.of("id", id, "total", original));

                assertEquals("USD:12.34", rawTotal(connection));
                Map<String, Object> row = session.selectOne("converted_orders", Map.of("total", original)).orElseThrow();
                assertEquals(original, row.get("total"));

                session.update("converted_orders", Map.of("total", updated), Map.of("total", original));
                row = session.selectOne("converted_orders", Map.of("total", updated)).orElseThrow();
                assertEquals(updated, row.get("total"));

                session.delete("converted_orders", Map.of("total", updated));
                assertFalse(session.exists("converted_orders", Map.of("id", id)));
            }
        }
    }

    @Test
    void convertedDynamicAssignedStringPrimaryKeyMustBeNonBlankBeforeInsert() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            orm.dynamicRegistry().register(convertedStringIdTable());
            try (DynamicSession session = orm.openDynamicSession()) {
                session.createTable("converted_string_users");

                assertThrows(MicroOrmException.class,
                        () -> session.insert("converted_string_users", Map.of("user_id", new UserId("   "), "name", "Blank id")));
                assertThrows(MicroOrmException.class,
                        () -> session.insertReturningId("converted_string_users", Map.of("user_id", new UserId("   "), "name", "Blank id")));

                UserId id = new UserId("ada");
                Object returnedId = session.insertReturningId("converted_string_users", Map.of("user_id", id, "name", "Ada"));

                assertEquals(id, returnedId);
                Map<String, Object> row = session.selectOne("converted_string_users", Map.of("user_id", id)).orElseThrow();
                assertEquals(id, row.get("user_id"));
                assertEquals("Ada", row.get("name"));
            }
        }
    }

    @Test
    void fluentDynamicQueriesUseConvertersAndMapNativeRows() throws SQLException {
        UUID id = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Money original = new Money("USD", new BigDecimal("12.34"));
        Money other = new Money("GBP", new BigDecimal("99.00"));
        Money updated = new Money("EUR", new BigDecimal("56.78"));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            orm.dynamicRegistry().register(convertedOrderTable());
            try (DynamicSession session = orm.openDynamicSession()) {
                session.createTable("converted_orders");
                session.insert("converted_orders", Map.of("id", id, "total", original));
                session.insert("converted_orders", Map.of("id", otherId, "total", other));

                assertEquals(2, session.selectRows(DynamicSelect.from("converted_orders")
                        .columns("id", "total")
                        .where(field("total").in(List.of(original, other)))
                        .orderBy(field("total").asc())).size());

                assertEquals(1, session.execute(DynamicUpdate.table("converted_orders")
                        .set("total", updated)
                        .where(field("total").eq(original))));

                Map<String, Object> row = session.selectOne(DynamicSelect.from("converted_orders")
                        .where(field("total").eq(updated)));
                assertEquals(updated, row.get("total"));

                try (Stream<Map<String, Object>> rows = session.streamRows(DynamicSelect.from("converted_orders")
                        .where(field("total").eq(updated)))) {
                    assertEquals(1, rows.count());
                }

                Query nativeQuery = Query.of("SELECT id, total FROM dynamic_converted_orders WHERE total = :total")
                        .set("total", "EUR:56.78");
                row = session.selectRows("converted_orders", nativeQuery).get(0);
                assertEquals(id, row.get("id"));
                assertEquals(updated, row.get("total"));

                assertEquals(1, session.execute(DynamicDelete.from("converted_orders")
                        .where(field("total").eq(updated))));
                assertFalse(session.exists("converted_orders", Map.of("id", id)));
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void createInsertSelectUpdateDelete(DialectTestSupport.DialectFixture dialect) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "datamart_docs", "sidecar");
            MicroOrm orm = newOrm(dialect, connection);
            try (DynamicSession session = orm.openDynamicSession()) {
            session.createTable("mart");
            assertTrue(session.tableExists("mart"));

            session.insert("mart", Map.of(
                    "UUID", id,
                    "VersionNR", 1,
                    "Removed", false,
                    "customer_name", "Acme"));

            assertTrue(session.exists("mart", Map.of("UUID", id)));
            assertEquals(1, session.selectAll("mart").size());

            Map<String, Object> row = session.selectOne("mart", Map.of("UUID", id)).orElseThrow();
            assertEquals("Acme", row.get("customer_name"));
            assertNumberEquals(1, row.get("VersionNR"));

            session.update("mart",
                    Map.of("customer_name", "Beta", "VersionNR", 2),
                    Map.of("UUID", id));

            row = session.selectOne("mart", Map.of("UUID", id)).orElseThrow();
            assertEquals("Beta", row.get("customer_name"));
            assertNumberEquals(2, row.get("VersionNR"));

            session.delete("mart", Map.of("UUID", id));
            assertFalse(session.exists("mart", Map.of("UUID", id)));
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void syncTableAddsNullableColumn(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "datamart_docs", "sidecar");
            MicroOrm orm = newOrm(dialect, connection);
            try (DynamicSession session = orm.openDynamicSession()) {
            session.createTable("mart");
            session.syncTable("mart");

            DynamicTable extended = DynamicTable.builder("mart")
                    .table("datamart_docs")
                    .column("UUID", LogicalType.UUID, c -> c.primaryKey().notNull())
                    .column("VersionNR", LogicalType.INT, Column.Builder::notNull)
                    .column("Removed", LogicalType.BOOL, Column.Builder::notNull)
                    .column("customer_name", LogicalType.STRING)
                    .column("notes", LogicalType.TEXT)
                    .build();
            orm.dynamicRegistry().register(extended);

            session.syncTable("mart");

            UUID id = UUID.randomUUID();
            session.insert("mart", Map.of(
                    "UUID", id,
                    "VersionNR", 1,
                    "Removed", false,
                    "notes", "hello"));

            Map<String, Object> row = session.selectOne("mart", Map.of("UUID", id)).orElseThrow();
            assertEquals("hello", row.get("notes"));
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void entitySessionStillWorksAlongsideDynamicSession(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "datamart_docs", "sidecar");
            MicroOrm orm = newOrm(dialect, connection).register(Sidecar.class);
            try (DynamicSession dynamicSession = orm.openDynamicSession();
                 Session entitySession = orm.openSession()) {
            dynamicSession.createTable("mart");
            entitySession.createEntity(Sidecar.class);

            assertTrue(dynamicSession.tableExists("mart"));
            assertEquals(0, entitySession.selectRows(Sidecar.class).size());
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void rejectsUnregisteredTable(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "datamart_docs", "sidecar");
            MicroOrm orm = dialect.createOrm(connection);
            try (DynamicSession session = orm.openDynamicSession()) {
            assertThrows(MicroOrmException.class, () -> session.createTable("unknown"));
            }
        }
    }

    private static void assertNumberEquals(int expected, Object actual) {
        assertEquals(expected, ((Number) actual).intValue());
    }

    private static DynamicTable generatedIdTable(GenerationType strategy) {
        return DynamicTable.builder("generated_items")
                .column("id", LogicalType.LONG, c -> c.primaryKey().generatedValue(strategy))
                .column("name", LogicalType.STRING, Column.Builder::notNull)
                .build();
    }

    private static DynamicTable generatedUuidTable() {
        return DynamicTable.builder("generated_uuid_items")
                .column("id", LogicalType.UUID, c -> c.primaryKey()
                        .uuidGenerator(UuidGenerator.Version.VERSION_7))
                .column("name", LogicalType.STRING, Column.Builder::notNull)
                .build();
    }

    private static DynamicTable assignedStringIdTable() {
        return DynamicTable.builder("string_users")
                .column("user_id", LogicalType.STRING, c -> c.primaryKey().length(64))
                .column("name", LogicalType.STRING, Column.Builder::notNull)
                .build();
    }

    private static DynamicTable convertedOrderTable() {
        return DynamicTable.builder("converted_orders")
                .table("dynamic_converted_orders")
                .column("id", LogicalType.UUID, Column.Builder::primaryKey)
                .column("total", LogicalType.STRING, c -> c.notNull().length(64).converter(MoneyConverter.class))
                .build();
    }

    private static DynamicTable convertedStringIdTable() {
        return DynamicTable.builder("converted_string_users")
                .column("user_id", LogicalType.STRING, c -> c.primaryKey().length(64).converter(UserIdConverter.class))
                .column("name", LogicalType.STRING, Column.Builder::notNull)
                .build();
    }

    private static String rawTotal(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT total FROM dynamic_converted_orders")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }
}
