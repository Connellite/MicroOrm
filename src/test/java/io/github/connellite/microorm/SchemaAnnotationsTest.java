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
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void createTableAppliesSchemaAnnotations() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection).register(SchemaOrder.class);

            try (Session session = orm.openSession()) {
                session.createEntity(SchemaOrder.class);
            }

            assertTrue(indexExists(connection, "schema_orders", "idx_schema_orders_status"));
            assertTrue(indexExists(connection, "schema_orders", "idx_schema_orders_status_total"));
            assertTrue(indexExists(connection, "schema_orders", "uk_schema_orders_code"));
            assertCreateSqlContains(connection, "schema_orders", "DEFAULT 'NEW'");
            assertCreateSqlContains(connection, "schema_orders", "CONSTRAINT ck_schema_orders_total CHECK (total >= 0)");
            assertCreateSqlContains(connection, "schema_orders", "CONSTRAINT uk_schema_orders_code UNIQUE (code)");
        }
    }

    @Test
    void syncTableAddsDefaultedNotNullColumnAndMissingIndexesIdempotently() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_orders_sync (id INTEGER PRIMARY KEY AUTOINCREMENT)");

            MicroOrm orm = MicroOrm.sqlite(connection).register(SchemaOrderSync.class);
            try (Session session = orm.openSession()) {
                session.syncEntity(SchemaOrderSync.class);
                session.syncEntity(SchemaOrderSync.class);
            }

            assertTrue(columnExists(connection, "schema_orders_sync", "status"));
            assertTrue(columnExists(connection, "schema_orders_sync", "code"));
            assertTrue(indexExists(connection, "schema_orders_sync", "idx_schema_orders_sync_status"));
            assertTrue(indexExists(connection, "schema_orders_sync", "uk_schema_orders_sync_code"));

            statement.execute("INSERT INTO schema_orders_sync (code) VALUES ('A-1')");
            try (ResultSet rs = statement.executeQuery("SELECT status FROM schema_orders_sync WHERE code = 'A-1'")) {
                assertTrue(rs.next());
                assertEquals("NEW", rs.getString(1));
                assertFalse(rs.next());
            }
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (rs.next()) {
                if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertCreateSqlContains(Connection connection, String tableName, String expected) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '" + tableName + "'")) {
            assertTrue(rs.next());
            assertTrue(rs.getString(1).contains(expected), rs.getString(1));
        }
    }
}
