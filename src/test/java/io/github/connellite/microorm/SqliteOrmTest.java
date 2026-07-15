package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.Query;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteOrmTest {

    @Entity(name = "widgets")
    public static class Widget {
        @Id
        private UUID id;

        @Column(nullable = false, indexed = true)
        private String name;

        public Widget() {
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity(name = "widgets")
    public static class WidgetWithDescription {
        @Id
        private UUID id;

        @Column(nullable = false, indexed = true)
        private String name;

        private String description;

        public WidgetWithDescription() {
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    @Entity(name = "numeric_widgets")
    public static class NumericWidget {
        @Id(autoIncrement = true)
        private long id;

        @Column(nullable = false)
        private String name;

        public NumericWidget() {
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity(name = "assigned_numeric_widgets")
    public static class AssignedNumericWidget {
        @Id
        private int id;

        @Column(nullable = false)
        private String name;

        public AssignedNumericWidget() {
        }

        public AssignedNumericWidget(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity(name = "invalid_string_id")
    public static class InvalidStringId {
        @Id
        private String id;

        public InvalidStringId() {
        }
    }

    @Entity(name = "invalid_uuid_autoincrement")
    public static class InvalidUuidAutoIncrementId {
        @Id(autoIncrement = true)
        private UUID id;

        public InvalidUuidAutoIncrementId() {
        }
    }

    @Test
    void crudLifecycle() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);

                Widget w = new Widget();
                w.setName("hello");
                s.insertRow(w);
                assertNotNull(w.getId());

                Widget loaded = s.selectRow(Widget.class, w.getId());
                assertNotNull(loaded);
                assertEquals("hello", loaded.getName());

                loaded.setName("world");
                assertEquals(1, s.updateRow(loaded));

                List<Widget> all = s.selectRows(Widget.class);
                assertEquals(1, all.size());
                assertEquals("world", all.get(0).getName());

                assertEquals(1, s.deleteRow(loaded));
                assertNull(s.selectRow(Widget.class, w.getId()));

                s.insertRow(newWidget("a"));
                s.insertRow(newWidget("b"));
                assertEquals(2, s.deleteAllRows(Widget.class));
                assertEquals(0, s.selectRows(Widget.class).size());
            }
        }
    }

    private static Widget newWidget(String name) {
        Widget w = new Widget();
        w.setName(name);
        return w;
    }

    private static NumericWidget newNumericWidget(String name) {
        NumericWidget w = new NumericWidget();
        w.setName(name);
        return w;
    }

    @Test
    void uuidIdSupportsGeneratedAndExplicitValues() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);

                Widget generated = s.insertRow(newWidget("generated"));
                assertNotNull(generated.getId());

                UUID explicitId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                Widget explicit = new Widget();
                explicit.id = explicitId;
                explicit.setName("explicit");
                s.insertRow(explicit);

                assertEquals(explicitId, explicit.getId());
                assertEquals("explicit", s.selectRow(Widget.class, explicitId).getName());
                assertTrue(s.existsById(Widget.class, generated.getId()));
            }
        }
    }

    @Test
    void numericAutoIncrementIdCrudLifecycle() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(NumericWidget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(NumericWidget.class);
                s.createEntity(NumericWidget.class);

                NumericWidget first = s.insertRow(newNumericWidget("first"));
                NumericWidget second = s.insertRow(newNumericWidget("second"));
                assertEquals(1L, first.getId());
                assertEquals(2L, second.getId());

                NumericWidget loaded = s.selectRow(NumericWidget.class, first.getId());
                assertNotNull(loaded);
                assertEquals("first", loaded.getName());

                loaded.setName("updated");
                assertEquals(1, s.updateRow(loaded));
                assertEquals("updated", s.selectRow(NumericWidget.class, first.getId()).getName());

                assertTrue(s.existsById(NumericWidget.class, second.getId()));
                assertEquals(1, s.deleteById(NumericWidget.class, second.getId()));
                assertNull(s.selectRow(NumericWidget.class, second.getId()));
            }
        }
    }

    @Test
    void numericAutoIncrementBatchFillsGeneratedIds() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(NumericWidget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(NumericWidget.class);
                s.createEntity(NumericWidget.class);

                List<NumericWidget> widgets = List.of(
                        newNumericWidget("a"),
                        newNumericWidget("b"),
                        newNumericWidget("c"));

                assertEquals(3, s.insertRows(widgets, 2));
                assertEquals(1L, widgets.get(0).getId());
                assertEquals(2L, widgets.get(1).getId());
                assertEquals(3L, widgets.get(2).getId());
                assertEquals(3, s.selectRows(NumericWidget.class).size());
            }
        }
    }

    @Test
    void explicitNumericIdIsSupported() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(AssignedNumericWidget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(AssignedNumericWidget.class);
                s.createEntity(AssignedNumericWidget.class);

                AssignedNumericWidget saved = s.insertRow(new AssignedNumericWidget(42, "answer"));
                assertEquals(42, saved.getId());
                assertEquals("answer", s.selectRow(AssignedNumericWidget.class, 42).getName());

                List<AssignedNumericWidget> more = List.of(
                        new AssignedNumericWidget(43, "next"),
                        new AssignedNumericWidget(44, "last"));
                assertEquals(2, s.insertRows(more, 1));
                assertEquals(3, s.selectRows(AssignedNumericWidget.class).size());
            }
        }
    }

    @Test
    void rejectsUnsupportedIdTypes() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            assertThrows(MicroOrmException.class, () -> MicroOrm.sqlite(c).register(InvalidStringId.class));
            assertThrows(MicroOrmException.class, () -> MicroOrm.sqlite(c).register(InvalidUuidAutoIncrementId.class));
        }
    }

    @Test
    void transactionCommit() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            UUID id;
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);
                s.beginTransaction();
                Widget w = new Widget();
                w.setName("tx");
                s.insertRow(w);
                id = w.getId();
                s.commitTransaction();
            }
            try (Session s2 = orm.openSession()) {
                Widget w = s2.selectRow(Widget.class, id);
                assertNotNull(w);
                assertEquals("tx", w.getName());
            }
        }
    }

    @Test
    void batchInsertExistsDeleteByIdAndFilteredSelects() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);

                List<Widget> widgets = List.of(newWidget("a"), newWidget("b"), newWidget("b"));
                assertEquals(3, s.insertRows(widgets, 2));
                assertNotNull(widgets.get(0).getId());
                assertTrue(s.existsById(Widget.class, widgets.get(1).getId()));

                assertEquals(2, s.selectRows(Widget.class, Map.of("name", "b")).size());
                try (var rows = s.streamRows(Widget.class, Map.of("name", "b"))) {
                    assertEquals(2, rows.count());
                }

                Map<String, Object> filters = new LinkedHashMap<>();
                filters.put("name", "a");
                assertEquals(1, s.selectRows(Widget.class, filters).size());

                Query byIds = Query.of("SELECT id, name FROM widgets WHERE id IN (:ids) ORDER BY name")
                        .setCollection("ids", List.of(widgets.get(0).getId(), widgets.get(2).getId()));
                List<Widget> selected = s.selectRows(Widget.class, byIds);
                assertEquals(2, selected.size());
                assertEquals("a", selected.get(0).getName());
                assertEquals("b", selected.get(1).getName());

                assertEquals(1, s.deleteById(Widget.class, widgets.get(1).getId()));
                assertEquals(2, s.selectRows(Widget.class).size());
            }
        }
    }

    @Test
    void entityQuerySelectsFilteredOrderedAndLimitedRows() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);
                s.insertRows(List.of(newWidget("a"), newWidget("b"), newWidget("b"), newWidget("c")));

                EntityQuery<Widget> query = EntityQuery.of(Widget.class)
                        .where(EntityQuery.field("name").in(List.of("b", "c")))
                        .orderBy(EntityQuery.field("name").desc())
                        .limit(2);

                List<Widget> selected = s.selectRows(query);
                assertEquals(2, selected.size());
                assertEquals("c", selected.get(0).getName());
                assertEquals("b", selected.get(1).getName());

                try (var rows = s.streamRows(query)) {
                    assertEquals(2, rows.count());
                }
            }
        }
    }

    @Test
    void sessionSingleResultHelpersHandleEmptySingleAndDuplicateRows() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);
                Widget first = s.insertRow(newWidget("a"));
                s.insertRow(newWidget("b"));
                s.insertRow(newWidget("b"));

                assertEquals("a", s.findById(Widget.class, first.getId()).orElseThrow().getName());
                assertFalse(s.findById(Widget.class, UUID.randomUUID()).isPresent());

                EntityQuery<Widget> oneByEntityQuery = EntityQuery.of(Widget.class)
                        .where(EntityQuery.field("name").eq("a"));
                assertEquals("a", s.selectOne(oneByEntityQuery).getName());
                assertEquals("a", s.findOne(oneByEntityQuery).orElseThrow().getName());

                EntityQuery<Widget> missingByEntityQuery = EntityQuery.of(Widget.class)
                        .where(EntityQuery.field("name").eq("missing"));
                assertFalse(s.findOne(missingByEntityQuery).isPresent());
                assertThrows(MicroOrmException.class, () -> s.selectOne(missingByEntityQuery));

                EntityQuery<Widget> duplicateByEntityQuery = EntityQuery.of(Widget.class)
                        .where(EntityQuery.field("name").eq("b"));
                assertThrows(MicroOrmException.class, () -> s.findOne(duplicateByEntityQuery));

                Query oneByRawQuery = Query.of("SELECT id, name FROM widgets WHERE name = :name")
                        .set("name", "a");
                assertEquals("a", s.selectOne(Widget.class, oneByRawQuery).getName());

                Query missingByRawQuery = Query.of("SELECT id, name FROM widgets WHERE name = :name")
                        .set("name", "missing");
                assertFalse(s.findOne(Widget.class, missingByRawQuery).isPresent());

                Query duplicateByRawQuery = Query.of("SELECT id, name FROM widgets WHERE name = :name")
                        .set("name", "b");
                assertThrows(MicroOrmException.class, () -> s.selectOne(Widget.class, duplicateByRawQuery));
            }
        }
    }

    @Test
    void rejectsSecondActiveStreamOnSameSession() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);
                s.insertRows(List.of(newWidget("a"), newWidget("b")));

                try (var first = s.streamRows(Widget.class)) {
                    assertThrows(MicroOrmException.class, () -> s.streamRows(Widget.class));
                }

                try (var second = s.streamRows(Widget.class)) {
                    assertEquals(2, second.count());
                }
            }
        }
    }

    @Test
    void repeatedCreateEntityPreservesExistingData() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.createEntity(Widget.class);
                Widget saved = s.insertRow(newWidget("startup-row"));
                UUID id = saved.getId();

                s.createEntity(Widget.class);
                s.createEntity(Widget.class);

                Widget loaded = s.selectRow(Widget.class, id);
                assertNotNull(loaded);
                assertEquals("startup-row", loaded.getName());
                assertEquals(1, s.selectRows(Widget.class).size());
            }
        }
    }

    @Test
    void repeatedUpdateEntityPreservesExistingData() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class, WidgetWithDescription.class);
            try (Session s = orm.openSession()) {
                s.createEntity(Widget.class);
                Widget saved = s.insertRow(newWidget("kept-on-update"));
                UUID id = saved.getId();

                s.updateEntity(Widget.class);
                s.updateEntity(Widget.class);

                Widget loaded = s.selectRow(Widget.class, id);
                assertNotNull(loaded);
                assertEquals("kept-on-update", loaded.getName());
                assertEquals(1, s.selectRows(Widget.class).size());
            }
        }
    }

    @Test
    void syncEntityAddsNullableColumnsWithoutDroppingData() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);
                Widget saved = s.insertRow(newWidget("kept"));

                s.syncEntity(WidgetWithDescription.class);

                WidgetWithDescription loaded = s.selectRow(WidgetWithDescription.class, saved.getId());
                assertNotNull(loaded);
                assertEquals(saved.getId(), loaded.getId());
                assertEquals("kept", loaded.getName());
                assertNull(loaded.getDescription());
            }
        }
    }
}
