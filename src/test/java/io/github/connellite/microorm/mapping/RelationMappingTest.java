package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.annotation.OneToMany;
import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.relation.LazyRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelationMappingTest {

    @Entity(name = "rel_customers")
    static class Customer {
        @Id
        private UUID id;
    }

    @Entity(name = "rel_orders")
    static class Order {
        @Id
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "customer_id")
        private LazyRef<Customer> customer;

        @OneToMany(mappedBy = "order")
        private LazyCollection<OrderItem> lines;
    }

    @Entity(name = "rel_order_items")
    static class OrderItem {
        @Id
        private long id;

        @ManyToOne
        @JoinColumn(name = "order_id")
        private LazyRef<Order> order;
    }

    @Test
    void registerBuildsRelationMetadata() {
        EntityModelRegistry registry = new EntityModelRegistry();
        EntityModel orderModel = registry.register(Order.class);
        registry.register(Customer.class);
        registry.register(OrderItem.class);

        assertEquals(1, orderModel.manyToOneRelations().size());
        assertEquals("customer_id", orderModel.manyToOneRelations().get(0).joinColumn());
        assertEquals(1, orderModel.oneToManyRelations().size());
        assertEquals("order", orderModel.oneToManyRelations().get(0).mappedBy());
        assertEquals(OrderItem.class, orderModel.oneToManyRelations().get(0).targetEntityClass());
    }

    @Entity(name = "bad_rel")
    static class MissingManyToOne {
        @Id
        private UUID id;

        private LazyRef<Customer> customer;
    }

    @Test
    void rejectsLazyRefWithoutManyToOne() {
        EntityModelRegistry registry = new EntityModelRegistry();
        registry.register(Customer.class);
        assertThrows(MicroOrmException.class, () -> registry.register(MissingManyToOne.class));
    }
}
