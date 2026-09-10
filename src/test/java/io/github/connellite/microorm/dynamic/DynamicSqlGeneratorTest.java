package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.dynamic.schema.MssqlDynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.MysqlDynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.OracleDynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.PostgresDynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.SqliteDynamicSchemaManager;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.JdbcValueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.connellite.microorm.dynamic.DynamicSelect.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicSqlGeneratorTest {

    private DynamicTable table;
    private DynamicSqlGenerator sql;

    @BeforeEach
    void setUp() {
        table = DynamicTable.builder("docs")
                .table("documents")
                .column("id", LogicalType.UUID, c -> c.primaryKey().notNull())
                .column("name", LogicalType.STRING, Column.Builder::notNull)
                .column("removed", LogicalType.BOOL)
                .build();
        sql = DynamicDialectSupport.sqlGenerator(SqliteDialect.getInstance());
    }

    @Test
    void insertUsesNamedParameters() {
        UUID id = UUID.randomUUID();
        BoundStatement stmt = sql.insert(table, Map.of(
                "id", id,
                "name", "alpha",
                "removed", false));

        assertTrue(stmt.sql().startsWith("INSERT INTO documents"));
        assertTrue(stmt.sql().contains(":id"));
        assertTrue(stmt.sql().contains(":name"));
        assertEquals(id.toString(), stmt.parameters().get("id"));
        assertEquals("alpha", stmt.parameters().get("name"));
    }

    @Test
    void identityInsertOmitsUnsetGeneratedPrimaryKey() {
        DynamicTable generatedTable = DynamicTable.builder("items")
                .column("id", LogicalType.LONG, c -> c.primaryKey().generatedValue(GenerationType.IDENTITY))
                .column("name", LogicalType.STRING, Column.Builder::notNull)
                .build();

        BoundStatement stmt = sql.insert(generatedTable, Map.of("name", "alpha"));

        assertEquals("INSERT INTO items (name) VALUES (:name)", stmt.sql());
        assertEquals(Map.of("name", "alpha"), stmt.parameters());
    }

    @Test
    void identityInsertIncludesExplicitPrimaryKey() {
        DynamicTable generatedTable = DynamicTable.builder("items")
                .column("id", LogicalType.LONG, c -> c.primaryKey().generatedValue(GenerationType.IDENTITY))
                .column("name", LogicalType.STRING, Column.Builder::notNull)
                .build();

        BoundStatement stmt = sql.insert(generatedTable, Map.of("id", 42L, "name", "alpha"));

        assertTrue(stmt.sql().contains(":id"));
        assertEquals(42L, stmt.parameters().get("id"));
    }

    @Test
    void updateSeparatesSetAndWhereParameterNames() {
        UUID id = UUID.randomUUID();
        BoundStatement stmt = sql.update(
                table,
                Map.of("name", "beta"),
                Map.of("id", id));

        assertTrue(stmt.sql().startsWith("UPDATE documents SET"));
        assertTrue(stmt.sql().contains("WHERE"));
        assertEquals("beta", stmt.parameters().get("set_name"));
        assertEquals(id.toString(), stmt.parameters().get("where_id"));
    }

    @Test
    void existsUsesLimitOne() {
        BoundStatement stmt = sql.exists(table, Map.of("id", UUID.randomUUID()));
        assertTrue(stmt.sql().contains("SELECT 1 FROM documents"));
        assertTrue(stmt.sql().endsWith("LIMIT 1"));
    }

    @Test
    void existsUsesMssqlTopOne() {
        DynamicSqlGenerator generator = DynamicDialectSupport.sqlGenerator(MssqlDialect.getInstance());

        BoundStatement stmt = generator.exists(table, Map.of("id", UUID.randomUUID()));

        assertTrue(stmt.sql().startsWith("SELECT TOP 1 1 FROM documents"));
    }

    @Test
    void existsUsesOracleFetchFirst() {
        DynamicSqlGenerator generator = DynamicDialectSupport.sqlGenerator(OracleDialect.getInstance());

        BoundStatement stmt = generator.exists(table, Map.of("id", UUID.randomUUID()));

        assertTrue(stmt.sql().endsWith("FETCH FIRST 1 ROWS ONLY"));
    }

    @Test
    void fluentSelectRendersCriteriaOrderingAndPagination() {
        BoundStatement stmt = sql.select(table, DynamicSelect.from("docs")
                .columns("id", "name")
                .where(field("name").like("A%"))
                .and(field("removed").eq(false))
                .orderBy(field("name").desc())
                .limit(10)
                .offset(5));

        assertEquals("SELECT documents.id, documents.name FROM documents"
                + " WHERE (documents.name LIKE :p1 AND documents.removed = :p2)"
                + " ORDER BY documents.name DESC LIMIT 10 OFFSET 5", stmt.sql());
        assertEquals("A%", stmt.parameters().get("p1"));
        assertEquals(false, stmt.parameters().get("p2"));
    }

    @Test
    void fluentSelectRendersInBetweenNullAndDistinctGroupHaving() {
        BoundStatement stmt = sql.select(table, DynamicSelect.from("docs")
                .distinct()
                .where(field("name").in(List.of("alpha", "beta")))
                .and(field("removed").isNotNull())
                .groupBy("removed")
                .having(field("name").between("a", "z")));

        assertEquals("SELECT DISTINCT documents.id, documents.name, documents.removed FROM documents"
                + " WHERE (documents.name IN (:p1) AND documents.removed IS NOT NULL)"
                + " GROUP BY documents.removed HAVING documents.name BETWEEN :p2 AND :p3", stmt.sql());
        assertEquals(List.of("alpha", "beta"), stmt.collectionParameters().get("p1"));
        assertEquals("a", stmt.parameters().get("p2"));
        assertEquals("z", stmt.parameters().get("p3"));
    }

    @Test
    void fluentUpdateRequiresWhereOrAllRows() {
        assertThrows(MicroOrmException.class, () -> sql.update(
                table,
                DynamicUpdate.table("docs").set("name", "beta")));

        BoundStatement stmt = sql.update(
                table,
                DynamicUpdate.table("docs").set("name", "beta").where(field("id").eq(UUID.fromString("00000000-0000-0000-0000-000000000001"))));

        assertEquals("UPDATE documents SET name = :set_name WHERE documents.id = :p1", stmt.sql());
        assertEquals("beta", stmt.parameters().get("set_name"));
        assertEquals("00000000-0000-0000-0000-000000000001", stmt.parameters().get("p1"));
    }

    @Test
    void fluentUpdateAllowsExplicitAllRows() {
        BoundStatement stmt = sql.update(table, DynamicUpdate.table("docs").set("removed", true).allRows());

        assertEquals("UPDATE documents SET removed = :set_removed", stmt.sql());
        assertEquals(true, stmt.parameters().get("set_removed"));
    }

    @Test
    void fluentDeleteRequiresWhereOrAllRows() {
        assertThrows(MicroOrmException.class, () -> sql.delete(table, DynamicDelete.from("docs")));

        BoundStatement stmt = sql.delete(table, DynamicDelete.from("docs").where(field("name").ne("alpha")));

        assertEquals("DELETE FROM documents WHERE documents.name <> :p1", stmt.sql());
        assertEquals("alpha", stmt.parameters().get("p1"));
    }

    @Test
    void fluentDeleteAllowsExplicitAllRows() {
        BoundStatement stmt = sql.delete(table, DynamicDelete.from("docs").allRows());

        assertEquals("DELETE FROM documents", stmt.sql());
    }

    @Test
    void fluentSelectUsesMssqlPagination() {
        DynamicSqlGenerator generator = DynamicDialectSupport.sqlGenerator(MssqlDialect.getInstance());

        BoundStatement top = generator.select(table, DynamicSelect.from("docs").limit(10));
        BoundStatement offset = generator.select(table, DynamicSelect.from("docs").offset(5));

        assertTrue(top.sql().startsWith("SELECT TOP 10 documents.id"));
        assertTrue(offset.sql().endsWith("ORDER BY (SELECT 1) OFFSET 5 ROWS"));
    }

    @Test
    void fluentSelectUsesOraclePagination() {
        DynamicSqlGenerator generator = DynamicDialectSupport.sqlGenerator(OracleDialect.getInstance());

        BoundStatement stmt = generator.select(table, DynamicSelect.from("docs").limit(10).offset(5));

        assertTrue(stmt.sql().endsWith("OFFSET 5 ROWS FETCH NEXT 10 ROWS ONLY"));
    }

    @Test
    void schemaManagerMatchesDialect() {
        assertInstanceOf(SqliteDynamicSchemaManager.class,
                DynamicDialectSupport.schemaManager(SqliteDialect.getInstance()));
        assertInstanceOf(PostgresDynamicSchemaManager.class,
                DynamicDialectSupport.schemaManager(PostgresDialect.getInstance()));
        assertInstanceOf(MysqlDynamicSchemaManager.class,
                DynamicDialectSupport.schemaManager(MysqlDialect.getInstance()));
        assertInstanceOf(MssqlDynamicSchemaManager.class,
                DynamicDialectSupport.schemaManager(MssqlDialect.getInstance()));
        assertInstanceOf(OracleDynamicSchemaManager.class,
                DynamicDialectSupport.schemaManager(OracleDialect.getInstance()));
    }

    @Test
    void schemaManagerRejectsUnsupportedDialect() {
        Dialect unsupported = new Dialect() {
            @Override
            public String sqlName(SqlIdentifier identifier) {
                return identifier.text();
            }

            @Override
            public String catalogName(SqlIdentifier identifier) {
                return identifier.text();
            }

            @Override
            public SqlGenerator sqlGenerator() {
                throw new UnsupportedOperationException();
            }

            @Override
            public JdbcValueMapper valueMapper() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void createTable(Connection c, EntityModel model) throws SQLException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void syncTable(Connection c, EntityModel model) throws SQLException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void dropTable(Connection c, EntityModel model) throws SQLException {
                throw new UnsupportedOperationException();
            }
        };

        assertThrows(MicroOrmException.class, () -> DynamicDialectSupport.schemaManager(unsupported));
    }
}
