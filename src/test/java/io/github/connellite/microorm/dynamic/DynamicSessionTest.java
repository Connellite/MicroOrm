package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.MicroOrm;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicSessionTest {

    @Entity(name = "sidecar")
    static class Sidecar {
        @Id
        private long id;
    }

    private Connection connection;
    private MicroOrm orm;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        orm = MicroOrm.sqlite(connection);
        DynamicTable table = DynamicTable.builder("mart")
                .table("datamart_docs")
                .column("UUID", LogicalType.UUID, c -> c.primaryKey().notNull())
                .column("VersionNR", LogicalType.INT, Column.Builder::notNull)
                .column("Removed", LogicalType.BOOL, Column.Builder::notNull)
                .column("customer_name", LogicalType.STRING)
                .build();
        orm.dynamicRegistry().register(table);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void createInsertSelectUpdateDelete() throws SQLException {
        UUID id = UUID.randomUUID();
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
            assertEquals(1, row.get("VersionNR"));

            session.update("mart",
                    Map.of("customer_name", "Beta", "VersionNR", 2),
                    Map.of("UUID", id));

            row = session.selectOne("mart", Map.of("UUID", id)).orElseThrow();
            assertEquals("Beta", row.get("customer_name"));
            assertEquals(2, row.get("VersionNR"));

            session.delete("mart", Map.of("UUID", id));
            assertFalse(session.exists("mart", Map.of("UUID", id)));
        }
    }

    @Test
    void syncTableAddsNullableColumn() throws SQLException {
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

    @Test
    void entitySessionStillWorksAlongsideDynamicSession() throws SQLException {
        orm.register(Sidecar.class);
        try (DynamicSession dynamicSession = orm.openDynamicSession();
             Session entitySession = orm.openSession()) {
            dynamicSession.createTable("mart");
            entitySession.createEntity(Sidecar.class);

            assertTrue(dynamicSession.tableExists("mart"));
            assertEquals(0, entitySession.selectRows(Sidecar.class).size());
        }
    }

    @Test
    void rejectsUnregisteredTable() throws SQLException {
        try (DynamicSession session = orm.openDynamicSession()) {
            assertThrows(MicroOrmException.class, () -> session.createTable("unknown"));
        }
    }
}
