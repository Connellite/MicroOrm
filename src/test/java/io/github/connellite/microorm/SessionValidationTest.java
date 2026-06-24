package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionValidationTest {

    @Entity(name = "validation_items")
    static class Item {
        @Id
        private java.util.UUID id;

        public Item() {
        }
    }

    @Test
    void insertRowRejectsNullEntity() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(NullPointerException.class, () -> session.insertRow(null));
            }
        }
    }

    @Test
    void updateRowRejectsNullEntity() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(NullPointerException.class, () -> session.updateRow(null));
            }
        }
    }

    @Test
    void deleteRowRejectsNullEntity() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(NullPointerException.class, () -> session.deleteRow(null));
            }
        }
    }

    @Test
    void batchInsertRejectsNullEntity() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                java.util.ArrayList<Item> batch = new java.util.ArrayList<>();
                batch.add(new Item());
                batch.add(null);
                assertThrows(IllegalArgumentException.class, () -> session.insertRows(batch));
            }
        }
    }
}
