package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Immutable;
import io.github.connellite.microorm.annotation.Subselect;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityMappingAnnotationTest {

    @Entity
    @Immutable
    @Table(name = "read_only_items")
    public static class ReadOnlyItem {
        @Id
        private long id;

        private String name;
    }

    @Entity
    @Table(name = "active_items")
    @Subselect("SELECT id, name FROM source_items WHERE active = 1")
    public static class ActiveItem {
        @Id
        private long id;

        private String name;
    }

    @Test
    void immutableEntityAllowsSelectOnly() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createReadOnlyTable(connection);
            MicroOrm orm = MicroOrm.sqlite(connection).register(ReadOnlyItem.class);
            try (Session session = orm.openSession()) {
                List<ReadOnlyItem> rows = session.selectRows(ReadOnlyItem.class);

                assertEquals(1, rows.size());
                assertEquals("locked", rows.get(0).name);
                ReadOnlyItem entity = rows.get(0);
                assertThrows(MicroOrmException.class, () -> session.createEntity(ReadOnlyItem.class));
                assertThrows(MicroOrmException.class, () -> session.insertRow(entity));
                assertThrows(MicroOrmException.class, () -> session.updateRow(entity));
                assertThrows(MicroOrmException.class, () -> session.deleteRow(entity));
                assertThrows(MicroOrmException.class, () -> session.deleteAllRows(ReadOnlyItem.class));
            }
        }
    }

    @Test
    void subselectEntityUsesSubquerySourceAndAllowsSelectOnly() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSourceTable(connection);
            MicroOrm orm = MicroOrm.sqlite(connection).register(ActiveItem.class);
            try (Session session = orm.openSession()) {
                List<ActiveItem> rows = session.selectRows(ActiveItem.class);

                assertEquals(1, rows.size());
                assertEquals("active", rows.get(0).name);
                assertThrows(MicroOrmException.class, () -> session.createEntity(ActiveItem.class));
                assertThrows(MicroOrmException.class, () -> session.insertRow(rows.get(0)));
                assertThrows(MicroOrmException.class, () -> session.deleteById(ActiveItem.class, rows.get(0).id));
            }
        }
    }

    private static void createReadOnlyTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE read_only_items (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO read_only_items (id, name) VALUES (1, 'locked')");
        }
    }

    private static void createSourceTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE source_items (id INTEGER PRIMARY KEY, name TEXT NOT NULL, active INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO source_items (id, name, active) VALUES (1, 'active', 1)");
            statement.executeUpdate("INSERT INTO source_items (id, name, active) VALUES (2, 'inactive', 0)");
        }
    }
}
