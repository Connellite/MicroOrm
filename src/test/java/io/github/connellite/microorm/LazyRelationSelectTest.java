package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.annotation.OneToMany;
import io.github.connellite.microorm.relation.LazyCollection;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LazyRelationSelectTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID ORDER_ID = UUID.fromString("bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Entity(name = "lazy_customers")
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

    @Entity(name = "lazy_orders")
    static class Order {
        @Id
        UUID id;

        @Column(nullable = false)
        String title;

        @ManyToOne
        @JoinColumn(name = "customer_id")
        LazyRef<Customer> customer;

        @OneToMany(mappedBy = "order")
        private LazyCollection<OrderItem> lines;

        Order() {
        }

        UUID getId() {
            return id;
        }

        String getTitle() {
            return title;
        }

        LazyRef<Customer> getCustomer() {
            return customer;
        }

        LazyCollection<OrderItem> getLines() {
            return lines;
        }
    }

    @Entity(name = "lazy_order_items")
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

        long getId() {
            return id;
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
                    CREATE TABLE lazy_customers (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE lazy_orders (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        customer_id TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE lazy_order_items (
                        id INTEGER PRIMARY KEY,
                        sku TEXT NOT NULL,
                        order_id TEXT NOT NULL
                    )
                    """);
            session.execute(Query.of("""
                    INSERT INTO lazy_customers (id, name) VALUES (:id, :name)
                    """).set("id", CUSTOMER_ID).set("name", "Acme"));
            session.execute(Query.of("""
                    INSERT INTO lazy_orders (id, title, customer_id) VALUES (:id, :title, :customerId)
                    """).set("id", ORDER_ID).set("title", "First order").set("customerId", CUSTOMER_ID));
            session.execute(Query.of("""
                    INSERT INTO lazy_order_items (id, sku, order_id) VALUES (:id, :sku, :orderId)
                    """).set("id", 1L).set("sku", "WIDGET").set("orderId", ORDER_ID));
            session.execute(Query.of("""
                    INSERT INTO lazy_order_items (id, sku, order_id) VALUES (:id, :sku, :orderId)
                    """).set("id", 2L).set("sku", "GADGET").set("orderId", ORDER_ID));
        }
    }

    @Test
    void manyToOneLoadsOnFirstGet() throws SQLException {
        try (Session session = orm.openSession()) {
            Order order = session.selectRow(Order.class, ORDER_ID);
            assertNotNull(order);
            assertFalse(order.getCustomer().isLoaded());

            Customer customer = order.getCustomer().get();
            assertNotNull(customer);
            assertEquals(CUSTOMER_ID, customer.getId());
            assertEquals("Acme", customer.getName());
            assertTrue(order.getCustomer().isLoaded());
        }
    }

    @Test
    void oneToManyLoadsOnFirstGet() throws SQLException {
        try (Session session = orm.openSession()) {
            Order order = session.selectRow(Order.class, ORDER_ID);
            assertNotNull(order);
            assertFalse(order.getLines().isLoaded());

            List<OrderItem> lines = order.getLines().get();
            assertEquals(2, lines.size());
            assertEquals(List.of("GADGET", "WIDGET"), lines.stream().map(OrderItem::getSku).sorted().toList());
            assertTrue(order.getLines().isLoaded());
        }
    }

    @Test
    void nullForeignKeyReturnsNullWithoutQuery() throws SQLException {
        UUID orphanOrderId = UUID.fromString("cccccccc-bbbb-cccc-dddd-eeeeeeeeeeee");
        try (Session session = orm.openSession()) {
            session.execute(Query.of("""
                    INSERT INTO lazy_orders (id, title, customer_id) VALUES (:id, :title, NULL)
                    """).set("id", orphanOrderId).set("title", "No customer"));

            Order order = session.selectRow(Order.class, orphanOrderId);
            assertNotNull(order);
            assertFalse(order.getCustomer().isSet());
            assertNull(order.getCustomer().get());
            assertFalse(order.getCustomer().isLoaded());
        }
    }

    @Test
    void lazyGetFailsAfterSessionClosed() throws SQLException {
        LazyRef<Customer> customerRef;
        try (Session session = orm.openSession()) {
            Order order = session.selectRow(Order.class, ORDER_ID);
            customerRef = order.getCustomer();
        }
        MicroOrmException ex = assertThrows(MicroOrmException.class, customerRef::get);
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void insertRowWithRelationsPersistsGraph() throws SQLException {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.title = "New";
        order.customer = LazyRef.toId(Customer.class, CUSTOMER_ID);

        try (Session session = orm.openSession()) {
            session.insertRow(order);
            Order loaded = session.selectRow(Order.class, order.id);
            assertEquals("New", loaded.getTitle());
            assertEquals(CUSTOMER_ID, loaded.getCustomer().get().getId());
        }
    }

    @Test
    void updateRowWithRelationsUpdatesScalarsAndFk() throws SQLException {
        try (Session session = orm.openSession()) {
            Order order = session.selectRow(Order.class, ORDER_ID);
            order.title = "Updated title";
            session.updateRow(order);
            Order loaded = session.selectRow(Order.class, ORDER_ID);
            assertEquals("Updated title", loaded.getTitle());
        }
    }
}
