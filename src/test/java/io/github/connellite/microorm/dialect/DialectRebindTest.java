package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.generation.IdGeneration;
import io.github.connellite.microorm.generation.SequenceTarget;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialectRebindTest {

    @Test
    void withValueMapperRebindsSqlGeneratorAndSchemaManager() {
        DefaultJdbcValueMapper mapper = new DefaultJdbcValueMapper(UuidStorage.BINARY);
        Dialect sqlite = SqliteDialect.getInstance();
        Dialect dialect = sqlite.withValueMapper(mapper);

        assertSame(mapper, dialect.valueMapper());
        assertSame(sqlite, dialect.unwrap());
        assertNotSame(sqlite.sqlGenerator(), dialect.sqlGenerator());
        assertNotSame(sqlite.schemaManager(), dialect.schemaManager());
        assertEquals("SELECT archive()", dialect.sqlGenerator().functionSql("archive", List.of()));
    }

    @Test
    void withValueMapperKeepsVendorSqlAndSequences() {
        Dialect dialect = OracleDialect.getInstance()
                .withValueMapper(new DefaultJdbcValueMapper(UuidStorage.STRING));

        assertTrue(dialect.supportsSequences());
        assertEquals(
                "SELECT count_active_users() FROM dual",
                dialect.sqlGenerator().functionSql("count_active_users", List.of()));
        assertEquals(
                "SELECT ITEMS_ID_SEQ.NEXTVAL FROM dual",
                dialect.nextSequenceValueSql(new SequenceTarget(
                        null,
                        SqlIdentifier.unquoted("items"),
                        SqlIdentifier.unquoted("id"),
                        IdGeneration.none())));
    }

    @Test
    void withValueMapperReplacesPreviousMapperWithoutNesting() {
        Dialect first = SqliteDialect.getInstance()
                .withValueMapper(new DefaultJdbcValueMapper(UuidStorage.BINARY));
        DefaultJdbcValueMapper secondMapper = new DefaultJdbcValueMapper(UuidStorage.STRING);
        Dialect second = first.withValueMapper(secondMapper);

        assertSame(secondMapper, second.valueMapper());
        assertSame(SqliteDialect.getInstance(), second.unwrap());
    }

    @Test
    void postgresWithValueMapperKeepsNextvalSql() {
        Dialect dialect = PostgresDialect.getInstance()
                .withValueMapper(new DefaultJdbcValueMapper(UuidStorage.STRING));

        assertTrue(dialect.nextSequenceValueSql(new SequenceTarget(
                null,
                SqlIdentifier.unquoted("items"),
                SqlIdentifier.unquoted("id"),
                IdGeneration.none())).contains("nextval"));
    }
}
