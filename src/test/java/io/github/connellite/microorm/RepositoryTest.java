package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.repository.EntityRepository;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.Query;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {

    @Entity(name = "repository_items")
    public static class RepositoryItem {
        @Id(autoIncrement = true)
        private long id;

        @Column(nullable = false)
        private String name;

        public RepositoryItem() {
        }

        RepositoryItem(String name) {
            this.name = name;
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

    interface RepositoryItemRepository extends EntityRepository<RepositoryItem, Long> {
        default Optional<RepositoryItem> findByName(String name) {
            return findOne(EntityQuery.of(RepositoryItem.class)
                    .where(EntityQuery.field(RepositoryItem::getName).eq(name)));
        }
    }

    interface BaseRepository<T, ID> extends EntityRepository<T, ID> {
    }

    interface IndirectRepositoryItemRepository extends BaseRepository<RepositoryItem, Long> {
    }

    @Test
    void onDemandRepositoryDelegatesToSessionMethods() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            RepositoryItemRepository repository = orm.repository(RepositoryItemRepository.class);

            repository.createEntity();
            RepositoryItem inserted = repository.insertRow(new RepositoryItem("first"));

            assertTrue(inserted.getId() > 0);
            assertTrue(repository.existsById(inserted.getId()));
            assertEquals("first", repository.selectRow(inserted.getId()).getName());
            assertEquals("first", repository.findById(inserted.getId()).orElseThrow().getName());
            assertEquals("first", repository.findByName("first").orElseThrow().getName());

            inserted.setName("renamed");
            assertEquals(1, repository.updateRow(inserted));
            assertEquals("renamed", repository.selectOne(Query.of(
                    "SELECT id, name FROM repository_items WHERE id = :id").set("id", inserted.getId())).getName());

            assertEquals(1, repository.deleteById(inserted.getId()));
            assertFalse(repository.findById(inserted.getId()).isPresent());
        }
    }

    @Test
    void sessionBoundRepositorySharesTransaction() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            try (Session session = orm.openSession()) {
                RepositoryItemRepository repository = session.repository(RepositoryItemRepository.class);
                repository.createEntity();

                session.beginTransaction();
                repository.insertRow(new RepositoryItem("tx"));
                session.commitTransaction();

                List<RepositoryItem> rows = repository.selectRows();
                assertEquals(1, rows.size());
                assertNotNull(rows.get(0));
                assertEquals("tx", rows.get(0).getName());
            }
        }
    }

    @Test
    void resolvesEntityTypeThroughIntermediateGenericRepositoryInterface() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection).register(RepositoryItem.class);
            IndirectRepositoryItemRepository repository = orm.repository(IndirectRepositoryItemRepository.class);

            repository.createEntity();
            RepositoryItem inserted = repository.insertRow(new RepositoryItem("indirect"));

            assertEquals("indirect", repository.selectRow(inserted.getId()).getName());
        }
    }
}
