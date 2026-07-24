package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.annotation.OneToMany;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.relation.EagerCollection;
import io.github.connellite.microorm.relation.EagerRef;
import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationSelectDialectTest {

    private static final UUID LAZY_CUSTOMER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000001");
    private static final UUID LAZY_ORDER_ID = UUID.fromString("bbbbbbbb-bbbb-cccc-dddd-000000000001");
    private static final UUID EAGER_CUSTOMER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000002");
    private static final UUID EAGER_ORDER_ID = UUID.fromString("bbbbbbbb-bbbb-cccc-dddd-000000000002");

    @Entity
    @Table(name = "dialect_lazy_customers")
    static class LazyCustomer {
        @Id
        private UUID id;

        @Column(nullable = false)
        private String name;

        UUID getId() {
            return id;
        }

        String getName() {
            return name;
        }
    }

    @Entity
    @Table(name = "dialect_lazy_orders")
    static class LazyOrder {
        @Id
        private UUID id;

        @Column(nullable = false)
        private String title;

        @ManyToOne
        @JoinColumn(name = "customer_id")
        private LazyRef<LazyCustomer> customer;

        @OneToMany(mappedBy = "order")
        private LazyCollection<LazyItem> lines;

        UUID getId() {
            return id;
        }

        String getTitle() {
            return title;
        }

        LazyRef<LazyCustomer> getCustomer() {
            return customer;
        }

        LazyCollection<LazyItem> getLines() {
            return lines;
        }
    }

    @Entity
    @Table(name = "dialect_lazy_items")
    static class LazyItem {
        @Id
        private long id;

        @Column(nullable = false)
        private String sku;

        @ManyToOne
        @JoinColumn(name = "order_id", nullable = false)
        private LazyRef<LazyOrder> order;

        String getSku() {
            return sku;
        }
    }

    @Entity
    @Table(name = "dialect_eager_customers")
    static class EagerCustomer {
        @Id
        private UUID id;

        @Column(nullable = false)
        private String name;

        UUID getId() {
            return id;
        }

        String getName() {
            return name;
        }
    }

    @Entity
    @Table(name = "dialect_eager_orders")
    static class EagerOrder {
        @Id
        private UUID id;

        @Column(nullable = false)
        private String title;

        @ManyToOne
        @JoinColumn(name = "customer_id")
        private EagerRef<EagerCustomer> customer;

        @OneToMany(mappedBy = "order")
        private EagerCollection<EagerItem> lines;

        String getTitle() {
            return title;
        }

        EagerRef<EagerCustomer> getCustomer() {
            return customer;
        }

        EagerCollection<EagerItem> getLines() {
            return lines;
        }
    }

    @Entity
    @Table(name = "dialect_eager_items")
    static class EagerItem {
        @Id
        private long id;

        @Column(nullable = false)
        private String sku;

        @ManyToOne
        @JoinColumn(name = "order_id", nullable = false)
        private LazyRef<EagerOrder> order;

        String getSku() {
            return sku;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void lazyRelationSelectAndJoinsWorkAcrossDialects(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            MicroOrm orm = newOrm(dialect, connection);
            seedLazyGraph(orm);

            try (Session session = orm.openSession()) {
                LazyOrder order = session.selectRow(LazyOrder.class, LAZY_ORDER_ID);
                assertNotNull(order);
                assertFalse(order.getCustomer().isLoaded());
                assertEquals(LAZY_CUSTOMER_ID, order.getCustomer().get().getId());
                assertTrue(order.getCustomer().isLoaded());
                assertEquals(List.of("GADGET", "WIDGET"), order.getLines().get().stream().map(LazyItem::getSku).sorted().toList());

                List<LazyOrder> byCustomer = session.selectRows(EntityQuery.of(LazyOrder.class)
                        .join("customer")
                        .where(EntityQuery.field("customer.name").eq("Acme")));
                assertEquals(1, byCustomer.size());
                assertEquals(LAZY_ORDER_ID, byCustomer.get(0).getId());

                List<LazyOrder> byLine = session.selectRows(EntityQuery.of(LazyOrder.class)
                        .leftJoin("lines")
                        .where(EntityQuery.field("lines.sku").in(List.of("WIDGET", "GADGET"))));
                assertEquals(1, byLine.size());
                assertEquals(LAZY_ORDER_ID, byLine.get(0).getId());

                LazyOrder inserted = new LazyOrder();
                inserted.id = UUID.randomUUID();
                inserted.title = "New";
                inserted.customer = LazyRef.toId(LazyCustomer.class, LAZY_CUSTOMER_ID);
                session.insertRow(inserted);
                assertEquals(LAZY_CUSTOMER_ID, session.selectRow(LazyOrder.class, inserted.id).getCustomer().get().getId());

                LazyOrder toUpdate = session.selectRow(LazyOrder.class, LAZY_ORDER_ID);
                toUpdate.title = "Updated title";
                session.updateRow(toUpdate);
                assertEquals("Updated title", session.selectRow(LazyOrder.class, LAZY_ORDER_ID).getTitle());
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void eagerRelationSelectWorksAcrossDialects(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            MicroOrm orm = newOrm(dialect, connection);
            seedEagerGraph(orm);

            EagerOrder order;
            try (Session session = orm.openSession()) {
                order = session.selectRow(EagerOrder.class, EAGER_ORDER_ID);
                assertNotNull(order);
                assertTrue(order.getCustomer().isLoaded());
                assertTrue(order.getLines().isLoaded());
            }
            assertEquals(EAGER_CUSTOMER_ID, order.getCustomer().get().getId());
            assertEquals("Acme", order.getCustomer().get().getName());
            assertEquals(List.of("GADGET", "WIDGET"), order.getLines().get().stream().map(EagerItem::getSku).sorted().toList());

            try (Session session = orm.openSession()) {
                EagerOrder orphan = new EagerOrder();
                orphan.id = UUID.randomUUID();
                orphan.title = "No customer";
                orphan.lines = EagerCollection.empty();
                session.insertRow(orphan);

                EagerOrder loaded = session.selectRow(EagerOrder.class, orphan.id);
                assertTrue(loaded.getCustomer().isNull());
                assertNull(loaded.getCustomer().get());

                EagerOrder inserted = new EagerOrder();
                inserted.id = UUID.randomUUID();
                inserted.title = "New";
                inserted.customer = EagerRef.toId(EagerCustomer.class, EAGER_CUSTOMER_ID);
                inserted.lines = EagerCollection.empty();
                session.insertRow(inserted);
                assertEquals(EAGER_CUSTOMER_ID, session.selectRow(EagerOrder.class, inserted.id).getCustomer().get().getId());
            }
        }
    }

    private static MicroOrm newOrm(DialectTestSupport.DialectFixture dialect, Connection connection) throws SQLException {
        DialectTestSupport.dropTables(connection,
                "dialect_lazy_items",
                "dialect_lazy_orders",
                "dialect_lazy_customers",
                "dialect_eager_items",
                "dialect_eager_orders",
                "dialect_eager_customers");
        MicroOrm orm = dialect.createOrm(connection)
                .register(LazyCustomer.class, LazyOrder.class, LazyItem.class, EagerCustomer.class, EagerOrder.class, EagerItem.class);
        try (Session session = orm.openSession()) {
            session.createEntity(LazyCustomer.class);
            session.createEntity(LazyOrder.class);
            session.createEntity(LazyItem.class);
            session.createEntity(EagerCustomer.class);
            session.createEntity(EagerOrder.class);
            session.createEntity(EagerItem.class);
        }
        return orm;
    }

    private static void seedLazyGraph(MicroOrm orm) throws SQLException {
        LazyCustomer customer = new LazyCustomer();
        customer.id = LAZY_CUSTOMER_ID;
        customer.name = "Acme";

        LazyOrder order = new LazyOrder();
        order.id = LAZY_ORDER_ID;
        order.title = "First order";
        order.customer = LazyRef.toId(LazyCustomer.class, LAZY_CUSTOMER_ID);

        LazyItem widget = new LazyItem();
        widget.id = 1L;
        widget.sku = "WIDGET";
        widget.order = LazyRef.toId(LazyOrder.class, LAZY_ORDER_ID);

        LazyItem gadget = new LazyItem();
        gadget.id = 2L;
        gadget.sku = "GADGET";
        gadget.order = LazyRef.toId(LazyOrder.class, LAZY_ORDER_ID);

        try (Session session = orm.openSession()) {
            session.insertRow(customer);
            session.insertRow(order);
            session.insertRow(widget);
            session.insertRow(gadget);
        }
    }

    private static void seedEagerGraph(MicroOrm orm) throws SQLException {
        EagerCustomer customer = new EagerCustomer();
        customer.id = EAGER_CUSTOMER_ID;
        customer.name = "Acme";

        EagerOrder order = new EagerOrder();
        order.id = EAGER_ORDER_ID;
        order.title = "First order";
        order.customer = EagerRef.toId(EagerCustomer.class, EAGER_CUSTOMER_ID);
        order.lines = EagerCollection.empty();

        EagerItem widget = new EagerItem();
        widget.id = 1L;
        widget.sku = "WIDGET";
        widget.order = LazyRef.toId(EagerOrder.class, EAGER_ORDER_ID);

        EagerItem gadget = new EagerItem();
        gadget.id = 2L;
        gadget.sku = "GADGET";
        gadget.order = LazyRef.toId(EagerOrder.class, EAGER_ORDER_ID);

        try (Session session = orm.openSession()) {
            session.insertRow(customer);
            session.insertRow(order);
            session.insertRow(widget);
            session.insertRow(gadget);
        }
    }

    private static Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }
}
