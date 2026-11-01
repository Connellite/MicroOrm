package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.EntityListeners;
import io.github.connellite.microorm.annotation.GeneratedValue;
import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.MappedSuperclass;
import io.github.connellite.microorm.annotation.PostLoad;
import io.github.connellite.microorm.annotation.PostPersist;
import io.github.connellite.microorm.annotation.PostRemove;
import io.github.connellite.microorm.annotation.PostUpdate;
import io.github.connellite.microorm.annotation.PrePersist;
import io.github.connellite.microorm.annotation.PreRemove;
import io.github.connellite.microorm.annotation.PreUpdate;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.mapping.LifecycleCallbacks;
import io.github.connellite.microorm.mapping.LifecycleEvent;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleCallbacksTest {

    @Entity
    @Table(name = "lifecycle_items")
    public static class LifecycleItem {
        static final List<String> EVENTS = new ArrayList<>();

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private long id;

        private String name;

        public LifecycleItem() {
        }

        LifecycleItem(String name) {
            this.name = name;
        }

        void setName(String name) {
            this.name = name;
        }

        @PrePersist
        private void beforeInsertA() {
            EVENTS.add("prePersistA:" + name);
        }

        @PrePersist
        private void beforeInsertB() {
            EVENTS.add("prePersistB:" + name);
        }

        @PostPersist
        private void afterInsert() {
            EVENTS.add("postPersist:" + id);
        }

        @PreUpdate
        private void beforeUpdate() {
            EVENTS.add("preUpdate:" + name);
        }

        @PostUpdate
        private void afterUpdate() {
            EVENTS.add("postUpdate:" + name);
        }

        @PreRemove
        private void beforeDelete() {
            EVENTS.add("preRemove:" + id);
        }

        @PostRemove
        private void afterDelete() {
            EVENTS.add("postRemove:" + id);
        }

        @PostLoad
        private void afterLoad() {
            EVENTS.add("postLoad:" + name);
        }
    }

    @Entity
    @Table(name = "invalid_lifecycle_items")
    public static class InvalidLifecycleItem {
        @Id
        private long id;

        @PrePersist
        String invalidCallback() {
            return "invalid";
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void invokesEntityLifecycleCallbacksAroundCrudAndLoad(DialectTestSupport.DialectFixture dialect) throws SQLException {
        LifecycleItem.EVENTS.clear();
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "lifecycle_items", "invalid_lifecycle_items");
            MicroOrm orm = dialect.createOrm(connection).register(LifecycleItem.class);
            try (Session session = orm.openSession()) {
                session.createEntity(LifecycleItem.class);

                LifecycleItem item = new LifecycleItem("new");
                session.insert(item);
                item.setName("updated");
                session.update(item);
                session.evict(item);
                session.select(LifecycleItem.class, item.id);
                session.delete(item);
            }
        }

        assertEquals(List.of(
                "prePersistA:new",
                "prePersistB:new",
                "postPersist:1",
                "preUpdate:updated",
                "postUpdate:updated",
                "postLoad:updated",
                "preRemove:1",
                "postRemove:1"), LifecycleItem.EVENTS);
    }

    @Test
    void rejectsInvalidLifecycleCallbackSignatures() {
        assertThrows(MicroOrmException.class, () -> new EntityModelRegistry().register(InvalidLifecycleItem.class));
    }

    @Test
    void listenerWrapsEntityCallbacksOnInsert() throws SQLException {
        ListenedItem.EVENTS.clear();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection).register(ListenedItem.class);
            try (Session session = orm.openSession()) {
                session.createEntity(ListenedItem.class);
                session.insert(new ListenedItem("new"));
            }
        }
        assertEquals(List.of(
                "listenerPre:new",
                "entityPre:new",
                "entityPost:1",
                "listenerPost:1"), ListenedItem.EVENTS);
    }

    @Test
    void listenersRunOutsideEntityCallbacksAndReverseAfterPost() {
        AuditedChild.EVENTS.clear();
        new EntityModelRegistry().register(AuditedChild.class);
        AuditedChild child = new AuditedChild();
        LifecycleCallbacks.invoke(child, LifecycleEvent.PRE_PERSIST);
        LifecycleCallbacks.invoke(child, LifecycleEvent.POST_PERSIST);
        assertEquals(List.of(
                "ancestor",
                "super",
                "first",
                "second",
                "entity",
                "entityPost",
                "secondPost",
                "firstPost",
                "superPost"), AuditedChild.EVENTS);
    }

    @Test
    void rejectsListenerWithoutPublicConstructor() {
        MicroOrmException error = assertThrows(
                MicroOrmException.class,
                () -> new EntityModelRegistry().register(HiddenCtorEntity.class));
        assertTrue(error.getMessage().contains("public no-arg constructor"));
    }

    @Test
    void rejectsListenerParameterThatDoesNotAcceptEntity() {
        MicroOrmException error = assertThrows(
                MicroOrmException.class,
                () -> new EntityModelRegistry().register(BadParameterEntity.class));
        assertTrue(error.getMessage().contains("must accept"));
    }

    private static Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }

    public static class InsertListener {
        @PrePersist
        public void beforeInsert(ListenedItem item) {
            ListenedItem.EVENTS.add("listenerPre:" + item.name);
        }

        @PostPersist
        public void afterInsert(ListenedItem item) {
            ListenedItem.EVENTS.add("listenerPost:" + item.id);
        }
    }

    @Entity
    @Table(name = "listened_items")
    @EntityListeners(InsertListener.class)
    public static class ListenedItem {
        static final List<String> EVENTS = new ArrayList<>();

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private long id;

        private String name;

        public ListenedItem() {
        }

        ListenedItem(String name) {
            this.name = name;
        }

        @PrePersist
        private void beforeInsert() {
            EVENTS.add("entityPre:" + name);
        }

        @PostPersist
        private void afterInsert() {
            EVENTS.add("entityPost:" + id);
        }
    }

    public static class InheritedListener {
        @PrePersist
        public void ancestor(AuditedBase entity) {
            AuditedChild.EVENTS.add("ancestor");
        }
    }

    public static class SuperListener extends InheritedListener {
        @PrePersist
        public void before(AuditedBase entity) {
            AuditedChild.EVENTS.add("super");
        }

        @PostPersist
        public void after(AuditedBase entity) {
            AuditedChild.EVENTS.add("superPost");
        }
    }

    public static class FirstListener {
        @PrePersist
        public void before(AuditedChild entity) {
            AuditedChild.EVENTS.add("first");
        }

        @PostPersist
        public void after(AuditedChild entity) {
            AuditedChild.EVENTS.add("firstPost");
        }
    }

    public static class SecondListener {
        @PrePersist
        public void before(AuditedChild entity) {
            AuditedChild.EVENTS.add("second");
        }

        @PostPersist
        public void after(AuditedChild entity) {
            AuditedChild.EVENTS.add("secondPost");
        }
    }

    @MappedSuperclass
    @EntityListeners(SuperListener.class)
    public static class AuditedBase {
    }

    @Entity
    @EntityListeners({FirstListener.class, SecondListener.class})
    public static class AuditedChild extends AuditedBase {
        static final List<String> EVENTS = new ArrayList<>();

        @Id
        private long id;

        public AuditedChild() {
        }

        @PrePersist
        private void beforeInsert() {
            EVENTS.add("entity");
        }

        @PostPersist
        private void afterInsert() {
            EVENTS.add("entityPost");
        }
    }

    public static class HiddenCtorListener {
        private HiddenCtorListener() {
        }

        @PrePersist
        public void before(HiddenCtorEntity entity) {
        }
    }

    @Entity
    @EntityListeners(HiddenCtorListener.class)
    public static class HiddenCtorEntity {
        @Id
        private long id;

        public HiddenCtorEntity() {
        }
    }

    public static class StringListener {
        @PrePersist
        public void before(String value) {
        }
    }

    @Entity
    @EntityListeners(StringListener.class)
    public static class BadParameterEntity {
        @Id
        private long id;

        public BadParameterEntity() {
        }
    }
}
