package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrmFeatureDialectTest {

    @Entity
    @Table(name = "feature_widgets")
    public static class FeatureWidget {
        @Id
        private UUID id;

        @Column(nullable = false, indexed = true)
        private String name;

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    @Entity
    @Table(name = "feature_widgets")
    public static class FeatureWidgetWithDescription {
        @Id
        private UUID id;

        @Column(nullable = false, indexed = true)
        private String name;

        private String description;

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
    @Table(name = "feature_assigned_numeric_widgets")
    public static class AssignedNumericWidget {
        @Id
        private int id;

        @Column(nullable = false)
        private String name;

        AssignedNumericWidget() {
        }

        AssignedNumericWidget(int id, String name) {
            this.id = id;
            this.name = name;
        }

        int getId() {
            return id;
        }

        String getName() {
            return name;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void entityQuerySelectsFilteredOrderedAndLimitedRows(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "feature_widgets", "feature_assigned_numeric_widgets");
            MicroOrm orm = dialect.createOrm(connection).register(FeatureWidget.class);
            try (Session session = orm.openSession()) {
                session.createEntity(FeatureWidget.class);
                session.insertRows(List.of(widget("a"), widget("b"), widget("b"), widget("c")));

                EntityQuery<FeatureWidget> query = EntityQuery.of(FeatureWidget.class)
                        .where(EntityQuery.field("name").in(List.of("b", "c")))
                        .orderBy(EntityQuery.field("name").desc())
                        .limit(2);

                List<FeatureWidget> selected = session.selectRows(query);
                assertEquals(2, selected.size());
                assertEquals("c", selected.get(0).getName());
                assertEquals("b", selected.get(1).getName());

                try (var rows = session.streamRows(query)) {
                    assertEquals(2, rows.count());
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void syncEntityAddsNullableColumnsWithoutDroppingData(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "feature_widgets", "feature_assigned_numeric_widgets");
            MicroOrm orm = dialect.createOrm(connection).register(FeatureWidget.class);
            UUID id;
            try (Session session = orm.openSession()) {
                session.createEntity(FeatureWidget.class);
                FeatureWidget saved = session.insertRow(widget("kept"));
                id = saved.getId();

                session.syncEntity(FeatureWidgetWithDescription.class);

                FeatureWidgetWithDescription loaded = session.selectRow(FeatureWidgetWithDescription.class, id);
                assertNotNull(loaded);
                assertEquals(id, loaded.getId());
                assertEquals("kept", loaded.getName());
                assertNull(loaded.getDescription());
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void explicitNumericIdIsSupported(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "feature_widgets", "feature_assigned_numeric_widgets");
            MicroOrm orm = dialect.createOrm(connection).register(AssignedNumericWidget.class);
            try (Session session = orm.openSession()) {
                session.createEntity(AssignedNumericWidget.class);

                AssignedNumericWidget saved = session.insertRow(new AssignedNumericWidget(42, "answer"));
                assertEquals(42, saved.getId());
                assertEquals("answer", session.selectRow(AssignedNumericWidget.class, 42).getName());

                assertEquals(2, session.insertRows(List.of(
                        new AssignedNumericWidget(43, "next"),
                        new AssignedNumericWidget(44, "last")), 1));
                assertEquals(3, session.selectRows(AssignedNumericWidget.class).size());
            }
        }
    }

    private static FeatureWidget widget(String name) {
        FeatureWidget widget = new FeatureWidget();
        widget.id = UUID.randomUUID();
        widget.name = name;
        return widget;
    }

    private static java.util.stream.Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }
}
