package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Param;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.query.EntitySelect;
import io.github.connellite.microorm.repository.EntityRepository;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.Query;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {

    @Entity
    @Table(name = "repository_items")
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
            return findOne(EntitySelect.of(RepositoryItem.class)
                    .where(EntitySelect.field(RepositoryItem::getName).eq(name)));
        }

        @io.github.connellite.microorm.annotation.Query(
                "SELECT id, name FROM repository_items WHERE name = :name")
        Optional<RepositoryItem> findNativeByName(@Param("name") String name);

        @io.github.connellite.microorm.annotation.Query(
                "SELECT id, name FROM repository_items WHERE name IN (:names)")
        List<RepositoryItem> findNativeByNames(@Param("names") List<String> names);

        @io.github.connellite.microorm.annotation.Query(
                "UPDATE repository_items SET name = :name WHERE id = :id")
        int renameNative(@Param("id") long id, @Param("name") String name);

        @io.github.connellite.microorm.annotation.Query(
                "DELETE FROM repository_items WHERE name = :name")
        boolean deleteNativeByName(@Param("name") String name);
    }

    interface BaseRepository<T, ID> extends EntityRepository<T, ID> {
    }

    interface IndirectRepositoryItemRepository extends BaseRepository<RepositoryItem, Long> {
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void onDemandRepositoryDelegatesToSessionMethods(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "repository_items");
            MicroOrm orm = dialect.createOrm(connection);
            RepositoryItemRepository repository = orm.repository(RepositoryItemRepository.class);

            repository.createEntity();
            RepositoryItem inserted = repository.insertRow(new RepositoryItem("first"));

            assertTrue(inserted.getId() > 0);
            assertTrue(repository.existsById(inserted.getId()));
            assertEquals("first", repository.selectRow(inserted.getId()).getName());
            assertEquals("first", repository.findById(inserted.getId()).orElseThrow().getName());
            assertEquals("first", repository.findByName("first").orElseThrow().getName());
            assertEquals("first", repository.findNativeByName("first").orElseThrow().getName());
            assertEquals(1, repository.findNativeByNames(List.of("first", "missing")).size());

            assertEquals(1, repository.renameNative(inserted.getId(), "renamed"));
            assertEquals("renamed", repository.selectOne(Query.of(
                    "SELECT id, name FROM repository_items WHERE id = :id").set("id", inserted.getId())).getName());

            assertTrue(repository.deleteNativeByName("renamed"));
            assertFalse(repository.findById(inserted.getId()).isPresent());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void sessionBoundRepositorySharesTransaction(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "repository_items");
            MicroOrm orm = dialect.createOrm(connection);
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void resolvesEntityTypeThroughIntermediateGenericRepositoryInterface(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "repository_items");
            MicroOrm orm = dialect.createOrm(connection).register(RepositoryItem.class);
            IndirectRepositoryItemRepository repository = orm.repository(IndirectRepositoryItemRepository.class);

            repository.createEntity();
            RepositoryItem inserted = repository.insertRow(new RepositoryItem("indirect"));

            assertEquals("indirect", repository.selectRow(inserted.getId()).getName());
        }
    }

    private static java.util.stream.Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }
}
