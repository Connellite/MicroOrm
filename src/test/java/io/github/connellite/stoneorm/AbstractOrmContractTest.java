package io.github.connellite.stoneorm;

import io.github.connellite.stoneorm.annotation.Column;
import io.github.connellite.stoneorm.annotation.Entity;
import io.github.connellite.stoneorm.annotation.Id;
import io.github.connellite.stoneorm.session.Session;
import io.github.connellite.stoneorm.sql.Query;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractOrmContractTest {

    @Entity(name = "contract_uuid_widgets")
    public static class UuidWidget {
        @Id
        private UUID id;

        @Column(nullable = false, indexed = true, length = 120)
        private String name;

        @Column(nullable = false)
        private boolean enabled;

        public UuidWidget() {
        }

        static UuidWidget of(String name, boolean enabled) {
            UuidWidget widget = new UuidWidget();
            widget.name = name;
            widget.enabled = enabled;
            return widget;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity(name = "contract_uuid_widgets")
    public static class UuidWidgetWithDescription {
        @Id
        private UUID id;

        @Column(nullable = false, indexed = true, length = 120)
        private String name;

        @Column(nullable = false)
        private boolean enabled;

        private String description;

        public UuidWidgetWithDescription() {
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getDescription() {
            return description;
        }
    }

    @Entity(name = "contract_numeric_widgets")
    public static class NumericWidget {
        @Id(autoIncrement = true)
        private long id;

        @Column(nullable = false, length = 120)
        private String name;

        public NumericWidget() {
        }

        static NumericWidget of(String name) {
            NumericWidget widget = new NumericWidget();
            widget.name = name;
            return widget;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    protected abstract Connection openConnection() throws SQLException;

    protected abstract Orm createOrm(Connection connection);

    protected abstract String quote(String identifier);

    protected abstract void assertUuidStorage(Connection connection) throws SQLException;

    @Test
    void uuidCrudStreamQueryAndStorageContract() throws SQLException {
        try (Connection connection = openConnection()) {
            Orm orm = createOrm(connection).register(UuidWidget.class, UuidWidgetWithDescription.class);
            try (Session session = orm.openSession()) {
                session.dropEntity(UuidWidget.class);
                session.createEntity(UuidWidget.class);
                assertUuidStorage(connection);

                UuidWidget generated = session.insertRow(UuidWidget.of("generated", true));
                assertNotNull(generated.getId());

                UUID explicitId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                UuidWidget explicit = UuidWidget.of("explicit", false);
                explicit.id = explicitId;
                session.insertRow(explicit);

                UuidWidget loaded = session.selectRow(UuidWidget.class, generated.getId());
                assertEquals("generated", loaded.getName());
                assertTrue(loaded.isEnabled());

                loaded.setName("renamed");
                assertEquals(1, session.updateRow(loaded));
                assertEquals("renamed", session.selectRow(UuidWidget.class, generated.getId()).getName());
                assertEquals("explicit", session.selectRow(UuidWidget.class, explicitId).getName());

                assertTrue(session.existsById(UuidWidget.class, generated.getId()));
                assertEquals(1, session.selectRows(UuidWidget.class, Map.of("name", "explicit")).size());
                try (var rows = session.streamRows(UuidWidget.class, Map.of("name", "explicit"))) {
                    assertEquals(1, rows.count());
                }

                Query byNames = Query.of("SELECT " + quote("id") + ", " + quote("name") + ", " + quote("enabled")
                                + " FROM " + quote("contract_uuid_widgets")
                                + " WHERE " + quote("name") + " IN (:names)")
                        .setCollection("names", List.of("renamed", "explicit"));
                assertEquals(2, session.selectRows(UuidWidget.class, byNames).size());

                session.syncEntity(UuidWidgetWithDescription.class);
                UuidWidgetWithDescription afterSync = session.selectRow(UuidWidgetWithDescription.class, generated.getId());
                assertEquals("renamed", afterSync.getName());
                assertNull(afterSync.getDescription());

                assertEquals(1, session.deleteById(UuidWidget.class, explicitId));
                assertNull(session.selectRow(UuidWidget.class, explicitId));
            }
        }
    }

    @Test
    void numericAutoIncrementAndBatchContract() throws SQLException {
        try (Connection connection = openConnection()) {
            Orm orm = createOrm(connection).register(NumericWidget.class);
            try (Session session = orm.openSession()) {
                session.dropEntity(NumericWidget.class);
                session.createEntity(NumericWidget.class);

                NumericWidget first = session.insertRow(NumericWidget.of("first"));
                assertTrue(first.getId() > 0);
                assertEquals("first", session.selectRow(NumericWidget.class, first.getId()).getName());

                List<NumericWidget> batch = List.of(
                        NumericWidget.of("a"),
                        NumericWidget.of("b"),
                        NumericWidget.of("c"));
                assertEquals(3, session.insertRows(batch, 2));
                assertTrue(batch.get(0).getId() > 0);
                assertTrue(batch.get(1).getId() > batch.get(0).getId());
                assertEquals(4, session.selectRows(NumericWidget.class).size());
            }
        }
    }
}
