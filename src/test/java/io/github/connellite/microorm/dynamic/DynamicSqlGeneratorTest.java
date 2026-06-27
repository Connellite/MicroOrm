package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.sql.BoundStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
