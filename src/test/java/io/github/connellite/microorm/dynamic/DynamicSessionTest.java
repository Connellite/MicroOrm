package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.MicroOrm;
import io.github.connellite.microorm.DialectTestSupport;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

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

    private static Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }
}
