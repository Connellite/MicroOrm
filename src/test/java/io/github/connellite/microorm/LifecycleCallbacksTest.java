package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.PostLoad;
import io.github.connellite.microorm.annotation.PostPersist;
import io.github.connellite.microorm.annotation.PostRemove;
import io.github.connellite.microorm.annotation.PostUpdate;
import io.github.connellite.microorm.annotation.PrePersist;
import io.github.connellite.microorm.annotation.PreRemove;
import io.github.connellite.microorm.annotation.PreUpdate;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifecycleCallbacksTest {

    @Entity(name = "lifecycle_items")
    public static class LifecycleItem {
        static final List<String> EVENTS = new ArrayList<>();

        @Id(autoIncrement = true)
        private long id;

        private String name;

        public LifecycleItem() {
        }

        LifecycleItem(String name) {
            this.name = name;
        }

        void setName(String name) {
            this.name = name;
        }

        @PrePersist
        private void beforeInsertA() {
            EVENTS.add("prePersistA:" + name);
        }

        @PrePersist
        private void beforeInsertB() {
            EVENTS.add("prePersistB:" + name);
        }

        @PostPersist
        private void afterInsert() {
            EVENTS.add("postPersist:" + id);
        }

        @PreUpdate
        private void beforeUpdate() {
            EVENTS.add("preUpdate:" + name);
        }

        @PostUpdate
        private void afterUpdate() {
            EVENTS.add("postUpdate:" + name);
        }

        @PreRemove
        private void beforeDelete() {
            EVENTS.add("preRemove:" + id);
        }

        @PostRemove
        private void afterDelete() {
            EVENTS.add("postRemove:" + id);
        }

        @PostLoad
        private void afterLoad() {
            EVENTS.add("postLoad:" + name);
        }
    }

    @Entity(name = "invalid_lifecycle_items")
    public static class InvalidLifecycleItem {
        @Id
        private long id;

        @PrePersist
        String invalidCallback() {
            return "invalid";
        }
    }

    @Test
    void invokesEntityLifecycleCallbacksAroundCrudAndLoad() throws SQLException {
        LifecycleItem.EVENTS.clear();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection).register(LifecycleItem.class);
            try (Session session = orm.openSession()) {
                session.createEntity(LifecycleItem.class);

                LifecycleItem item = new LifecycleItem("new");
                session.insertRow(item);
                item.setName("updated");
                session.updateRow(item);
                session.selectRow(LifecycleItem.class, item.id);
                session.deleteRow(item);
            }
        }

        assertEquals(List.of(
                "prePersistA:new",
                "prePersistB:new",
                "postPersist:1",
                "preUpdate:updated",
                "postUpdate:updated",
                "postLoad:updated",
                "preRemove:1",
                "postRemove:1"), LifecycleItem.EVENTS);
    }

    @Test
    void rejectsInvalidLifecycleCallbackSignatures() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);

            assertThrows(MicroOrmException.class, () -> orm.register(InvalidLifecycleItem.class));
        }
    }
}
