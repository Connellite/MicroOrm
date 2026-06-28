package io.github.connellite.microorm.dynamic;

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
import java.util.Map;
import java.util.UUID;

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
