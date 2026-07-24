package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Check;
import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.ColumnDefault;
import io.github.connellite.microorm.annotation.Comment;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Index;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.UniqueConstraint;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaAnnotationsTest {

    @Entity
    @Comment("orders table")
    @Check(name = "ck_schema_orders_total", constraints = "total >= 0")
    @Table(
            name = "schema_orders",
            indexes = {
                    @Index(name = "idx_schema_orders_status", columnList = "status"),
                    @Index(name = "idx_schema_orders_status_total", columnList = "status ASC, total DESC")
            },
            uniqueConstraints = @UniqueConstraint(name = "uk_schema_orders_code", columnNames = "code"))
    public static class SchemaOrder {
        @Id(autoIncrement = true)
        private long id;

        @Column(nullable = false, length = 32)
        @ColumnDefault("'NEW'")
        @Comment("business status")
        private String status;

        @Column(length = 64)
        private String code;

        @Column(nullable = false)
        private int total;
    }

    @Entity
    @Table(
            name = "schema_orders_sync",
            indexes = @Index(name = "idx_schema_orders_sync_status", columnList = "status"),
            uniqueConstraints = @UniqueConstraint(name = "uk_schema_orders_sync_code", columnNames = "code"))
    public static class SchemaOrderSync {
        @Id(autoIncrement = true)
        private long id;

        @Column(nullable = false, length = 32)
        @ColumnDefault("'NEW'")
        private String status;

        @Column(length = 64)
        private String code;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void createTableAppliesSchemaAnnotations(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "schema_orders", "schema_orders_sync");
            MicroOrm orm = dialect.createOrm(connection).register(SchemaOrder.class);

            try (Session session = orm.openSession()) {
                session.createEntity(SchemaOrder.class);
            }

            assertTrue(DialectTestSupport.indexExists(connection, "schema_orders", "idx_schema_orders_status"));
            assertTrue(DialectTestSupport.indexExists(connection, "schema_orders", "idx_schema_orders_status_total"));
            assertTrue(DialectTestSupport.indexExists(connection, "schema_orders", "uk_schema_orders_code"));
            assertDefaultValueIsApplied(connection, "schema_orders");
            assertCheckConstraintIsApplied(connection, "schema_orders");
            assertUniqueConstraintIsApplied(connection, "schema_orders");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void syncTableAddsDefaultedNotNullColumnAndMissingIndexesIdempotently(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection();
             Statement statement = connection.createStatement()) {
            DialectTestSupport.dropTables(connection, "schema_orders", "schema_orders_sync");
            statement.execute(dialect.autoIncrementIdTableDdl("schema_orders_sync"));

            MicroOrm orm = dialect.createOrm(connection).register(SchemaOrderSync.class);
            try (Session session = orm.openSession()) {
                session.syncEntity(SchemaOrderSync.class);
                session.syncEntity(SchemaOrderSync.class);
            }

            assertTrue(DialectTestSupport.columnExists(connection, "schema_orders_sync", "status"));
            assertTrue(DialectTestSupport.columnExists(connection, "schema_orders_sync", "code"));
            assertTrue(DialectTestSupport.indexExists(connection, "schema_orders_sync", "idx_schema_orders_sync_status"));
            assertTrue(DialectTestSupport.indexExists(connection, "schema_orders_sync", "uk_schema_orders_sync_code"));

            statement.execute("INSERT INTO schema_orders_sync (code) VALUES ('A-1')");
            try (ResultSet rs = statement.executeQuery("SELECT status FROM schema_orders_sync WHERE code = 'A-1'")) {
                assertTrue(rs.next());
                assertEquals("NEW", rs.getString(1));
                assertFalse(rs.next());
            }
        }
    }

    private static Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }

    private static void assertDefaultValueIsApplied(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + tableName + " (code, total) VALUES ('D-1', 10)");
            try (ResultSet rs = statement.executeQuery("SELECT status FROM " + tableName + " WHERE code = 'D-1'")) {
                assertTrue(rs.next());
                assertEquals("NEW", rs.getString(1));
                assertFalse(rs.next());
            }
        }
    }

    private static void assertCheckConstraintIsApplied(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class,
                    () -> statement.execute("INSERT INTO " + tableName + " (status, code, total) VALUES ('NEW', 'C-1', -1)"));
        }
    }

    private static void assertUniqueConstraintIsApplied(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + tableName + " (status, code, total) VALUES ('NEW', 'U-1', 1)");
            assertThrows(SQLException.class,
                    () -> statement.execute("INSERT INTO " + tableName + " (status, code, total) VALUES ('NEW', 'U-1', 2)"));
        }
    }

}
