package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

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

    @Test
    void selectRowRejectsNullId() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(MicroOrmException.class, () -> session.selectRow(Item.class, null));
            }
        }
    }

    @Test
    void deleteByIdRejectsNullId() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(MicroOrmException.class, () -> session.deleteById(Item.class, null));
            }
        }
    }

    @Entity(name = "pk_only")
    static class PkOnly {
        @Id
        private UUID id;

        public PkOnly() {
        }
    }

    @Test
    void updateRowRejectsEntityWithOnlyPrimaryKey() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(c).register(PkOnly.class);
            try (Session session = orm.openSession()) {
                session.createEntity(PkOnly.class);
                PkOnly row = new PkOnly();
                row.id = UUID.randomUUID();
                session.insertRow(row);
                assertThrows(MicroOrmException.class, () -> session.updateRow(row));
            }
        }
    }
}
