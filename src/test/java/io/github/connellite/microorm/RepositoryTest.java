package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Param;
import io.github.connellite.microorm.annotation.Procedure;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.query.EntitySelect;
import io.github.connellite.microorm.repository.EntityRepository;
import io.github.connellite.microorm.repository.RepositoryProxyFactory;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        @Procedure("repository_insert_item")
        void insertByProcedure(@Param("name") String name);

        @Procedure(procedureName = "repository_rename_item")
        void renameByProcedure(@Param("id") long id, @Param("name") String name);

        @Procedure("repository_item_label")
        String labelByFunction(@Param("name") String name);

        @Procedure(procedureName = "repository_count_items")
        long countByFunction();
    }

    interface BaseRepository<T, ID> extends EntityRepository<T, ID> {
    }

    interface IndirectRepositoryItemRepository extends BaseRepository<RepositoryItem, Long> {
    }

    interface InvalidAnnotatedRepository extends EntityRepository<RepositoryItem, Long> {
        @io.github.connellite.microorm.annotation.Query("SELECT id, name FROM repository_items")
        @Procedure("repository_items_proc")
        void conflictingAnnotations();
    }

    interface ProcedureRepository extends EntityRepository<RepositoryItem, Long> {
        @Procedure("repository_items_proc")
        void callProcedure(@Param("name") String name);
    }

    interface MssqlProcedureRepository extends EntityRepository<RepositoryItem, Long> {
        @Procedure("EXEC repository_insert_item @p_name = :name")
        void insertByProcedure(@Param("name") String name);

        @Procedure("EXEC repository_rename_item @p_id = :id, @p_name = :name")
        void renameByProcedure(@Param("id") long id, @Param("name") String name);

        @Procedure("SELECT dbo.repository_item_label(:name)")
        String labelByFunction(@Param("name") String name);

        @Procedure("SELECT dbo.repository_count_items()")
        long countByFunction();
    }

    interface OracleFunctionRepository extends EntityRepository<RepositoryItem, Long> {
        @Procedure("SELECT repository_item_label(:name) FROM dual")
        String labelByFunction(@Param("name") String name);

        @Procedure("SELECT repository_count_items() FROM dual")
        long countByFunction();
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("procedureDialects")
    void procedureAnnotationCallsRealStoredProcedures(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "repository_items");
            MicroOrm orm = dialect.createOrm(connection);
            RepositoryItemRepository repository = orm.repository(RepositoryItemRepository.class);
            repository.createEntity();
            createRepositoryProcedures(connection, dialect);

            if ("MSSQL".equals(dialect.name())) {
                MssqlProcedureRepository procedureRepository = orm.repository(MssqlProcedureRepository.class);
                procedureRepository.insertByProcedure("from_proc");
            } else {
                repository.insertByProcedure("from_proc");
            }
            RepositoryItem inserted = repository.findNativeByName("from_proc").orElseThrow();

            assertEquals("fn:from_proc", labelByFunction(orm, dialect, "from_proc"));
            assertEquals(1L, countByFunction(orm, dialect));

            if ("MSSQL".equals(dialect.name())) {
                MssqlProcedureRepository procedureRepository = orm.repository(MssqlProcedureRepository.class);
                procedureRepository.renameByProcedure(inserted.getId(), "renamed_proc");
            } else {
                repository.renameByProcedure(inserted.getId(), "renamed_proc");
            }

            assertEquals("renamed_proc", repository.selectRow(inserted.getId()).getName());
        }
    }

    @Test
    void rejectsRepositoryMethodWithQueryAndProcedure() {
        InvalidAnnotatedRepository repository = RepositoryProxyFactory.create(InvalidAnnotatedRepository.class, operation -> null);

        assertThrows(MicroOrmException.class, repository::conflictingAnnotations);
    }

    @Test
    void routesProcedureAnnotatedMethodsThroughNativeQueryHandling() {
        final boolean[] invoked = {false};
        ProcedureRepository repository = RepositoryProxyFactory.create(
                ProcedureRepository.class,
                operation -> {
                    invoked[0] = true;
                    return null;
                });

        repository.callProcedure("first");
        assertTrue(invoked[0]);
    }

    private static Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }

    private static Stream<DialectTestSupport.DialectFixture> procedureDialects() {
        return Stream.of(
                DialectTestSupport.postgres(),
                DialectTestSupport.mysql(),
                DialectTestSupport.mssql(),
                DialectTestSupport.oracle());
    }

    private static String labelByFunction(MicroOrm orm, DialectTestSupport.DialectFixture dialect, String name) {
        return switch (dialect.name()) {
            case "MSSQL" -> orm.repository(MssqlProcedureRepository.class).labelByFunction(name);
            case "Oracle" -> orm.repository(OracleFunctionRepository.class).labelByFunction(name);
            default -> orm.repository(RepositoryItemRepository.class).labelByFunction(name);
        };
    }

    private static long countByFunction(MicroOrm orm, DialectTestSupport.DialectFixture dialect) {
        return switch (dialect.name()) {
            case "MSSQL" -> orm.repository(MssqlProcedureRepository.class).countByFunction();
            case "Oracle" -> orm.repository(OracleFunctionRepository.class).countByFunction();
            default -> orm.repository(RepositoryItemRepository.class).countByFunction();
        };
    }

    private static void createRepositoryProcedures(
            Connection connection,
            DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            switch (dialect.name()) {
                case "PostgreSQL" -> {
                    statement.execute("DROP PROCEDURE IF EXISTS repository_insert_item(VARCHAR)");
                    statement.execute("DROP PROCEDURE IF EXISTS repository_rename_item(BIGINT, VARCHAR)");
                    statement.execute("DROP FUNCTION IF EXISTS repository_item_label(VARCHAR)");
                    statement.execute("DROP FUNCTION IF EXISTS repository_count_items()");
                    statement.execute("""
                            CREATE PROCEDURE repository_insert_item(IN p_name VARCHAR)
                            LANGUAGE SQL
                            AS $$
                                INSERT INTO repository_items(name) VALUES (p_name);
                            $$
                            """);
                    statement.execute("""
                            CREATE PROCEDURE repository_rename_item(IN p_id BIGINT, IN p_name VARCHAR)
                            LANGUAGE SQL
                            AS $$
                                UPDATE repository_items SET name = p_name WHERE id = p_id;
                            $$
                            """);
                    statement.execute("""
                            CREATE FUNCTION repository_item_label(p_name VARCHAR)
                            RETURNS VARCHAR
                            LANGUAGE SQL
                            AS $$
                                SELECT 'fn:' || p_name;
                            $$
                            """);
                    statement.execute("""
                            CREATE FUNCTION repository_count_items()
                            RETURNS BIGINT
                            LANGUAGE SQL
                            AS $$
                                SELECT 1;
                            $$
                            """);
                }
                case "MySQL" -> {
                    trustMySqlFunctionCreators(connection);
                    statement.execute("DROP PROCEDURE IF EXISTS repository_insert_item");
                    statement.execute("DROP PROCEDURE IF EXISTS repository_rename_item");
                    statement.execute("DROP FUNCTION IF EXISTS repository_item_label");
                    statement.execute("DROP FUNCTION IF EXISTS repository_count_items");
                    statement.execute("""
                            CREATE PROCEDURE repository_insert_item(IN p_name VARCHAR(255))
                            BEGIN
                                INSERT INTO repository_items(name) VALUES (p_name);
                            END
                            """);
                    statement.execute("""
                            CREATE PROCEDURE repository_rename_item(IN p_id BIGINT, IN p_name VARCHAR(255))
                            BEGIN
                                UPDATE repository_items SET name = p_name WHERE id = p_id;
                            END
                            """);
                    statement.execute("""
                            CREATE FUNCTION repository_item_label(p_name VARCHAR(255))
                            RETURNS VARCHAR(255)
                            DETERMINISTIC
                            NO SQL
                            RETURN CONCAT('fn:', p_name)
                            """);
                    statement.execute("""
                            CREATE FUNCTION repository_count_items()
                            RETURNS BIGINT
                            DETERMINISTIC
                            NO SQL
                            RETURN 1
                            """);
                }
                case "MSSQL" -> {
                    statement.execute("IF OBJECT_ID('repository_insert_item', 'P') IS NOT NULL DROP PROCEDURE repository_insert_item");
                    statement.execute("IF OBJECT_ID('repository_rename_item', 'P') IS NOT NULL DROP PROCEDURE repository_rename_item");
                    statement.execute("IF OBJECT_ID('repository_item_label', 'FN') IS NOT NULL DROP FUNCTION repository_item_label");
                    statement.execute("IF OBJECT_ID('repository_count_items', 'FN') IS NOT NULL DROP FUNCTION repository_count_items");
                    statement.execute("""
                            CREATE PROCEDURE repository_insert_item
                                @p_name NVARCHAR(255)
                            AS
                            BEGIN
                                SET NOCOUNT ON;
                                INSERT INTO repository_items(name) VALUES (@p_name);
                            END
                            """);
                    statement.execute("""
                            CREATE PROCEDURE repository_rename_item
                                @p_id BIGINT,
                                @p_name NVARCHAR(255)
                            AS
                            BEGIN
                                SET NOCOUNT ON;
                                UPDATE repository_items SET name = @p_name WHERE id = @p_id;
                            END
                            """);
                    statement.execute("""
                            CREATE FUNCTION repository_item_label(@p_name NVARCHAR(255))
                            RETURNS NVARCHAR(255)
                            AS
                            BEGIN
                                RETURN CONCAT('fn:', @p_name);
                            END
                            """);
                    statement.execute("""
                            CREATE FUNCTION repository_count_items()
                            RETURNS BIGINT
                            AS
                            BEGIN
                                RETURN 1;
                            END
                            """);
                }
                case "Oracle" -> {
                    statement.execute("""
                            CREATE OR REPLACE PROCEDURE repository_insert_item(p_name IN VARCHAR2)
                            AS
                            BEGIN
                                INSERT INTO repository_items(name) VALUES (p_name);
                            END;
                            """);
                    statement.execute("""
                            CREATE OR REPLACE PROCEDURE repository_rename_item(p_id IN NUMBER, p_name IN VARCHAR2)
                            AS
                            BEGIN
                                UPDATE repository_items SET name = p_name WHERE id = p_id;
                            END;
                            """);
                    statement.execute("""
                            CREATE OR REPLACE FUNCTION repository_item_label(p_name IN VARCHAR2)
                            RETURN VARCHAR2
                            AS
                            BEGIN
                                RETURN 'fn:' || p_name;
                            END;
                            """);
                    statement.execute("""
                            CREATE OR REPLACE FUNCTION repository_count_items
                            RETURN NUMBER
                            AS
                            BEGIN
                                RETURN 1;
                            END;
                            """);
                }
                default -> throw new IllegalArgumentException("Procedures are not supported by test fixture: " + dialect.name());
            }
        }
    }

    private static void trustMySqlFunctionCreators(Connection connection) {
        try (Connection root = DriverManager.getConnection(connection.getMetaData().getURL(), "root", "test");
             Statement statement = root.createStatement()) {
            statement.execute("SET GLOBAL log_bin_trust_function_creators = 1");
        } catch (SQLException ignored) {
            // Some MySQL environments do not enable binary logging and do not require this setting.
        }
    }
}
