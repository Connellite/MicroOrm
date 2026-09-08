package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.GeneratedValue;
import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.annotation.GenericGenerator;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.UuidGenerator;
import io.github.connellite.microorm.connection.KeepOpenConnectionProvider;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.query.EntitySelect;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.schema.SqliteSchemaManager;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.JdbcValueMapper;
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

    @Entity
    @Table(name = "widgets")
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

    @Entity
    @Table(name = "uuid_generated_widgets")
    public static class UuidGeneratedWidget {
        @Id
        @UuidGenerator(version = UuidGenerator.Version.VERSION_7)
        private UUID id;

        @Column(nullable = false)
        private String name;

        public UuidGeneratedWidget() {
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

    @Entity
    @Table(name = "widgets")
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

    @Entity
    @Table(name = "numeric_widgets")
    public static class NumericWidget {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Entity
    @Table(name = "native_numeric_widgets")
    public static class NativeNumericWidget {
        @Id
        @GenericGenerator(name = "native_generator", strategy = "native")
        @GeneratedValue(generator = "native_generator")
        private Long id;

        @Column(nullable = false)
        private String name;

        public NativeNumericWidget() {
        }

        public Long getId() {
            return id;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity
    @Table(name = "sequence_numeric_widgets")
    public static class SequenceNumericWidget {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;

        @Column(nullable = false)
        private String name;

        public SequenceNumericWidget() {
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity
    @Table(name = "assigned_numeric_widgets")
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

    @Entity
    @Table(name = "assigned_string_users")
    public static class AssignedStringUser {
        @Id
        @Column(name = "user_id", length = 64)
        private String userId;

        @Column(nullable = false)
        private String name;

        public AssignedStringUser() {
        }

        public AssignedStringUser(String userId, String name) {
            this.userId = userId;
            this.name = name;
        }

        public String getUserId() {
            return userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity
    @Table(name = "invalid_boolean_id")
    public static class InvalidBooleanId {
        @Id
        private boolean id;

        public InvalidBooleanId() {
        }
    }

    @Entity
    @Table(name = "invalid_uuid_autoincrement")
    public static class InvalidUuidAutoIncrementId {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    private static SequenceNumericWidget newSequenceWidget(String name) {
        SequenceNumericWidget w = new SequenceNumericWidget();
        w.setName(name);
        return w;
    }

    private static MicroOrm sequenceTestOrm(Connection connection) {
        return new MicroOrm(
                new SqliteSequenceTestDialect(),
                new KeepOpenConnectionProvider(connection),
                new EntityModelRegistry());
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
    void uuidGeneratorUsesConfiguredUuidVersion() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(UuidGeneratedWidget.class);
            try (Session s = orm.openSession()) {
                s.createEntity(UuidGeneratedWidget.class);

                UuidGeneratedWidget generated = new UuidGeneratedWidget();
                generated.setName("uuid7");
                s.insertRow(generated);

                assertNotNull(generated.getId());
                assertEquals(7, generated.getId().version());
                assertEquals("uuid7", s.selectRow(UuidGeneratedWidget.class, generated.getId()).getName());
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
    void genericNativeGeneratorUsesIdentityGeneratedKeys() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(NativeNumericWidget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(NativeNumericWidget.class);
                s.createEntity(NativeNumericWidget.class);

                NativeNumericWidget widget = new NativeNumericWidget();
                widget.setName("native");
                s.insertRow(widget);

                assertEquals(1L, widget.getId());
            }
        }
    }

    @Test
    void sqliteRejectsSequenceGeneratedIds() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(SequenceNumericWidget.class);
            try (Session s = orm.openSession()) {
                MicroOrmException error = assertThrows(MicroOrmException.class,
                        () -> s.createEntity(SequenceNumericWidget.class));
                assertTrue(error.getMessage().contains("GenerationType.SEQUENCE"));
            }
        }
    }

    @Test
    void sequenceGeneratedIdIsAllocatedBeforeInsert() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = sequenceTestOrm(c).register(SequenceNumericWidget.class);
            try (Session s = orm.openSession()) {
                s.createEntity(SequenceNumericWidget.class);

                SequenceNumericWidget widget = new SequenceNumericWidget();
                widget.setName("sequence");
                s.insertRow(widget);

                assertEquals(1L, widget.getId());
                assertEquals("sequence", s.selectRow(SequenceNumericWidget.class, widget.getId()).getName());
            }
        }
    }

    @Test
    void sequenceGeneratedBatchAllocatesIdsBeforeInsert() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = sequenceTestOrm(c).register(SequenceNumericWidget.class);
            try (Session s = orm.openSession()) {
                s.createEntity(SequenceNumericWidget.class);
                List<SequenceNumericWidget> widgets = List.of(
                        newSequenceWidget("a"),
                        newSequenceWidget("b"),
                        newSequenceWidget("c"));

                assertEquals(3, s.insertRows(widgets, 2));
                assertEquals(1L, widgets.get(0).getId());
                assertEquals(2L, widgets.get(1).getId());
                assertEquals(3L, widgets.get(2).getId());
                assertEquals(3, s.selectRows(SequenceNumericWidget.class).size());
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
    void assignedStringIdMustBeNonBlankBeforePersist() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(AssignedStringUser.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(AssignedStringUser.class);
                s.createEntity(AssignedStringUser.class);

                AssignedStringUser saved = s.insertRow(new AssignedStringUser("ada", "Ada"));
                assertEquals("ada", saved.getUserId());
                AssignedStringUser loaded = s.selectRow(AssignedStringUser.class, "ada");
                assertEquals("Ada", loaded.getName());

                loaded.setName("Ada Lovelace");
                assertEquals(1, s.updateRow(loaded));
                assertEquals("Ada Lovelace", s.selectRow(AssignedStringUser.class, "ada").getName());

                MicroOrmException missing = assertThrows(MicroOrmException.class,
                        () -> s.insertRow(new AssignedStringUser()));
                assertTrue(missing.getMessage().contains("Primary key"));
                MicroOrmException blank = assertThrows(MicroOrmException.class,
                        () -> s.insertRow(new AssignedStringUser("  ", "blank")));
                assertTrue(blank.getMessage().contains("Primary key"));

                assertEquals(1, s.deleteRow(loaded));
                assertNull(s.selectRow(AssignedStringUser.class, "ada"));
            }
        }
    }

    @Test
    void rejectsUnsupportedIdTypes() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            assertThrows(MicroOrmException.class, () -> MicroOrm.sqlite(c).register(InvalidBooleanId.class));
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
    void EntitySelectSelectsFilteredOrderedAndLimitedRows() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);
                s.insertRows(List.of(newWidget("a"), newWidget("b"), newWidget("b"), newWidget("c")));

                EntitySelect<Widget> query = EntitySelect.of(Widget.class)
                        .where(EntitySelect.field("name").in(List.of("b", "c")))
                        .orderBy(EntitySelect.field("name").desc())
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

                EntitySelect<Widget> oneByEntitySelect = EntitySelect.of(Widget.class)
                        .where(EntitySelect.field("name").eq("a"));
                assertEquals("a", s.selectOne(oneByEntitySelect).getName());
                assertEquals("a", s.findOne(oneByEntitySelect).orElseThrow().getName());

                EntitySelect<Widget> missingByEntitySelect = EntitySelect.of(Widget.class)
                        .where(EntitySelect.field("name").eq("missing"));
                assertFalse(s.findOne(missingByEntitySelect).isPresent());
                assertThrows(MicroOrmException.class, () -> s.selectOne(missingByEntitySelect));

                EntitySelect<Widget> duplicateByEntitySelect = EntitySelect.of(Widget.class)
                        .where(EntitySelect.field("name").eq("b"));
                assertThrows(MicroOrmException.class, () -> s.findOne(duplicateByEntitySelect));

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

    private static final class SqliteSequenceTestDialect implements Dialect {
        private final SqliteDialect delegate = SqliteDialect.getInstance();
        private final SchemaManager schemaManager = new SqliteSchemaManager(this);

        @Override
        public String sqlName(SqlIdentifier identifier) {
            return delegate.sqlName(identifier);
        }

        @Override
        public String catalogName(SqlIdentifier identifier) {
            return delegate.catalogName(identifier);
        }

        @Override
        public String jdbcColumnLabel(SqlIdentifier identifier) {
            return delegate.jdbcColumnLabel(identifier);
        }

        @Override
        public SqlGenerator sqlGenerator() {
            return delegate.sqlGenerator();
        }

        @Override
        public JdbcValueMapper valueMapper() {
            return delegate.valueMapper();
        }

        @Override
        public boolean supportsSequences() {
            return true;
        }

        @Override
        public String createSequenceDdl(EntityModel model, EntityField pk) {
            return "CREATE TABLE IF NOT EXISTS microorm_sequence_values (id INTEGER PRIMARY KEY AUTOINCREMENT)";
        }

        @Override
        public String nextSequenceValueSql(EntityModel model, EntityField pk) {
            return "INSERT INTO microorm_sequence_values DEFAULT VALUES RETURNING id";
        }

        @Override
        public void createTable(Connection c, EntityModel model) throws SQLException {
            schemaManager.createTable(c, model);
        }

        @Override
        public void syncTable(Connection c, EntityModel model) throws SQLException {
            schemaManager.syncTable(c, model);
        }

        @Override
        public void dropTable(Connection c, EntityModel model) throws SQLException {
            schemaManager.dropTable(c, model);
        }
    }
}
