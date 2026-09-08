package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.annotation.CascadeType;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.JoinTable;
import io.github.connellite.microorm.annotation.ManyToMany;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.annotation.OneToMany;
import io.github.connellite.microorm.annotation.OneToOne;
import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.relation.LazyRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationMappingTest {

    @Entity
    @Table(name = "rel_customers")
    static class Customer {
        @Id
        private UUID id;
    }

    @Entity
    @Table(name = "rel_orders")
    static class Order {
        @Id
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "customer_id")
        private LazyRef<Customer> customer;

        @OneToMany(mappedBy = "order")
        private LazyCollection<OrderItem> lines;
    }

    @Entity
    @Table(name = "rel_order_items")
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
        assertFalse(orderModel.manyToOneRelations().get(0).cascades(CascadeType.PERSIST));
        assertFalse(orderModel.oneToManyRelations().get(0).cascades(CascadeType.ALL));
        assertFalse(orderModel.oneToManyRelations().get(0).orphanRemoval());
    }

    @Entity
    @Table(name = "bad_rel")
    static class MissingManyToOne {
        @Id
        private UUID id;

        private LazyRef<Customer> customer;
    }

    @Entity
    @Table(name = "rel_authors")
    static class Author {
        @Id
        private UUID id;

        @ManyToMany
        @JoinTable(
                name = "rel_author_books",
                joinColumns = @JoinColumn(name = "author_id"),
                inverseJoinColumns = @JoinColumn(name = "book_id"))
        private LazyCollection<Book> books;
    }

    @Entity
    @Table(name = "rel_books")
    static class Book {
        @Id
        private UUID id;

        @ManyToMany(mappedBy = "books")
        private LazyCollection<Author> authors;
    }

    @Test
    void registerBuildsManyToManyMetadata() {
        EntityModelRegistry registry = new EntityModelRegistry();
        EntityModel authorModel = registry.register(Author.class);
        EntityModel bookModel = registry.register(Book.class);

        assertEquals(1, authorModel.manyToManyRelations().size());
        ManyToManyField owning = authorModel.manyToManyRelations().get(0);
        assertEquals("rel_author_books", owning.joinTable());
        assertEquals("author_id", owning.ownerJoinColumn());
        assertEquals("book_id", owning.targetJoinColumn());
        assertEquals(Book.class, owning.targetEntityClass());
        assertTrue(owning.owning());
        assertFalse(owning.cascades(CascadeType.PERSIST));

        ManyToManyField inverse = bookModel.manyToManyRelations().get(0);
        assertFalse(inverse.owning());
        assertEquals("books", inverse.mappedBy());
        assertEquals(owning.joinTable(), inverse.owningSide(registry).joinTable());
    }

    @Entity
    @Table(name = "bad_m2m")
    static class MissingManyToMany {
        @Id
        private UUID id;

        private LazyCollection<Book> books;
    }

    @Test
    void rejectsCollectionWithoutRelationAnnotation() {
        EntityModelRegistry registry = new EntityModelRegistry();
        registry.register(Book.class);
        assertThrows(MicroOrmException.class, () -> registry.register(MissingManyToMany.class));
    }

    @Test
    void rejectsLazyRefWithoutManyToOne() {
        EntityModelRegistry registry = new EntityModelRegistry();
        registry.register(Customer.class);
        assertThrows(MicroOrmException.class, () -> registry.register(MissingManyToOne.class));
    }

    @Entity
    @Table(name = "rel_users")
    static class User {
        @Id
        private UUID id;

        @OneToOne
        @JoinColumn(name = "profile_id")
        private LazyRef<Profile> profile;
    }

    @Entity
    @Table(name = "rel_profiles")
    static class Profile {
        @Id
        private UUID id;

        @OneToOne(mappedBy = "profile")
        private LazyRef<User> user;
    }

    @Test
    void registerBuildsOneToOneMetadata() {
        EntityModelRegistry registry = new EntityModelRegistry();
        EntityModel userModel = registry.register(User.class);
        EntityModel profileModel = registry.register(Profile.class);

        assertEquals(1, userModel.oneToOneRelations().size());
        OneToOneField owning = userModel.oneToOneRelations().get(0);
        assertTrue(owning.owning());
        assertEquals(Profile.class, owning.targetEntityClass());
        assertFalse(owning.cascades(CascadeType.PERSIST));
        assertFalse(owning.orphanRemoval());
        assertEquals("profile_id", userModel.manyToOneByFieldName("profile").joinColumn());
        assertTrue(userModel.manyToOneByFieldName("profile").unique());
        assertTrue(userModel.manyToOneByFieldName("profile").nullable());

        OneToOneField inverse = profileModel.oneToOneRelations().get(0);
        assertFalse(inverse.owning());
        assertEquals("profile", inverse.mappedBy());
        assertEquals(owning.javaField().getName(), inverse.owningSide(registry).javaField().getName());
    }

    @Entity
    @Table(name = "rel_optional_false_orders")
    static class OptionalFalseOrder {
        @Id
        private UUID id;

        @ManyToOne(optional = false)
        @JoinColumn(name = "customer_id")
        private LazyRef<Customer> customer;

        @ManyToOne
        @JoinColumn(name = "alt_customer_id", nullable = false)
        private LazyRef<Customer> altCustomer;
    }

    @Test
    void manyToOneNullabilityFollowsHibernateOptionalAndJoinColumn() {
        EntityModelRegistry registry = new EntityModelRegistry();
        registry.register(Customer.class);
        EntityModel model = registry.register(OptionalFalseOrder.class);

        assertFalse(model.manyToOneByFieldName("customer").nullable());
        assertFalse(model.manyToOneByFieldName("altCustomer").nullable());
        EntityModel orderModel = registry.register(Order.class);
        assertTrue(orderModel.manyToOneByFieldName("customer").nullable());
    }
}
