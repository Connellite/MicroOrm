package io.github.connellite.microorm.relation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LazyRefTest {

    static class Customer {
    }

    @Test
    void isNullWhenForeignKeyAndAttachedEntityAreAbsent() {
        LazyRef<Customer> ref = LazyRef.of(null, Customer.class, null);
        assertTrue(ref.isNull());
        assertFalse(ref.isSet());
        assertNull(ref.foreignKey());
        assertNull(ref.get());
    }

    @Test
    void isNotNullWhenForeignKeyIsPresent() {
        UUID id = UUID.randomUUID();
        LazyRef<Customer> ref = LazyRef.toId(Customer.class, id);
        assertFalse(ref.isNull());
        assertTrue(ref.isSet());
        assertSame(id, ref.foreignKey());
    }

    @Test
    void isNotNullWhenEntityIsAttached() {
        Customer customer = new Customer();
        LazyRef<Customer> ref = LazyRef.to(customer);
        assertFalse(ref.isNull());
        assertTrue(ref.isSet());
        assertNull(ref.foreignKey());
        assertSame(customer, ref.attachedEntity());
    }

    @Test
    void isNullIsOppositeOfIsSet() {
        LazyRef<Customer> unset = LazyRef.of(null, Customer.class, null);
        LazyRef<Customer> byId = LazyRef.toId(Customer.class, 1L);
        assertTrue(unset.isNull() == !unset.isSet());
        assertTrue(byId.isNull() == !byId.isSet());
    }
}
