package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.annotation.OneToMany;
import io.github.connellite.microorm.relation.EagerCollection;
import io.github.connellite.microorm.relation.EagerRef;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EagerRelationSelectTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-ffffffffffff");
    private static final UUID ORDER_ID = UUID.fromString("bbbbbbbb-bbbb-cccc-dddd-ffffffffffff");

    @Entity(name = "eager_customers")
    static class Customer {
        @Id
        private UUID id;

        @Column(nullable = false)
        private String name;

        Customer() {
        }

        UUID getId() {
            return id;
        }

        String getName() {
            return name;
        }
    }

    @Entity(name = "eager_orders")
    static class Order {
        @Id
        UUID id;

        @Column(nullable = false)
        String title;

        @ManyToOne
        @JoinColumn(name = "customer_id")
        EagerRef<Customer> customer;

        @OneToMany(mappedBy = "order")
        private EagerCollection<OrderItem> lines;

        Order() {
        }

        String getTitle() {
            return title;
        }

        EagerRef<Customer> getCustomer() {
            return customer;
        }

        EagerCollection<OrderItem> getLines() {
            return lines;
        }
    }

    @Entity(name = "eager_order_items")
    static class OrderItem {
        @Id
        private long id;

        @Column(nullable = false)
        private String sku;

        @ManyToOne
        @JoinColumn(name = "order_id", nullable = false)
        private LazyRef<Order> order;

        OrderItem() {
        }

        String getSku() {
            return sku;
        }
    }

    private MicroOrm orm;

    @BeforeEach
    void setUp() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        orm = MicroOrm.sqlite(connection).register(Customer.class, Order.class, OrderItem.class);
        try (Session session = orm.openSession();
             Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE eager_customers (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE eager_orders (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        customer_id TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE eager_order_items (
                        id INTEGER PRIMARY KEY,
                        sku TEXT NOT NULL,
                        order_id TEXT NOT NULL
                    )
                    """);
            session.execute(Query.of("""
                    INSERT INTO eager_customers (id, name) VALUES (:id, :name)
                    """).set("id", CUSTOMER_ID).set("name", "Acme"));
            session.execute(Query.of("""
                    INSERT INTO eager_orders (id, title, customer_id) VALUES (:id, :title, :customerId)
                    """).set("id", ORDER_ID).set("title", "First order").set("customerId", CUSTOMER_ID));
            session.execute(Query.of("""
                    INSERT INTO eager_order_items (id, sku, order_id) VALUES (:id, :sku, :orderId)
                    """).set("id", 1L).set("sku", "WIDGET").set("orderId", ORDER_ID));
            session.execute(Query.of("""
                    INSERT INTO eager_order_items (id, sku, order_id) VALUES (:id, :sku, :orderId)
                    """).set("id", 2L).set("sku", "GADGET").set("orderId", ORDER_ID));
        }
    }

    @Test
    void eagerRelationsAreAvailableAfterSessionClosed() throws SQLException {
        Order order;
        try (Session session = orm.openSession()) {
            order = session.selectRow(Order.class, ORDER_ID);
            assertNotNull(order);
            assertTrue(order.getCustomer().isLoaded());
            assertTrue(order.getLines().isLoaded());
        }

        assertEquals(CUSTOMER_ID, order.getCustomer().get().getId());
        assertEquals("Acme", order.getCustomer().get().getName());
        assertEquals(List.of("GADGET", "WIDGET"), order.getLines().get().stream().map(OrderItem::getSku).sorted().toList());
    }

    @Test
    void nullForeignKeyProducesEmptyEagerRef() throws SQLException {
        UUID orphanOrderId = UUID.fromString("cccccccc-bbbb-cccc-dddd-ffffffffffff");
        try (Session session = orm.openSession()) {
            session.execute(Query.of("""
                    INSERT INTO eager_orders (id, title, customer_id) VALUES (:id, :title, NULL)
                    """).set("id", orphanOrderId).set("title", "No customer"));

            Order order = session.selectRow(Order.class, orphanOrderId);
            assertNotNull(order);
            assertTrue(order.getCustomer().isNull());
            assertNull(order.getCustomer().get());
        }
    }

    @Test
    void insertRowWithEagerRefPersistsForeignKey() throws SQLException {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.title = "New";
        order.customer = EagerRef.toId(Customer.class, CUSTOMER_ID);
        order.lines = EagerCollection.empty();

        try (Session session = orm.openSession()) {
            session.insertRow(order);
            Order loaded = session.selectRow(Order.class, order.id);
            assertEquals("New", loaded.getTitle());
            assertEquals(CUSTOMER_ID, loaded.getCustomer().get().getId());
        }
    }
}
