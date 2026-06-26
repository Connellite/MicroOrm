package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlIdentifierTest {

    @Test
    void parseUnquotedIdentifier() {
        SqlIdentifier id = SqlIdentifier.parse("document_id");
        assertEquals("document_id", id.text());
        assertFalse(id.quoted());
    }

    @Test
    void parseBacktickQuotedIdentifier() {
        SqlIdentifier id = SqlIdentifier.parse("`size`");
        assertEquals("size", id.text());
        assertTrue(id.quoted());
    }

    @Test
    void parseRejectsBlank() {
        assertThrows(MicroOrmException.class, () -> SqlIdentifier.parse("  "));
    }

    @Test
    void dialectRendersUnquotedAndQuotedNames() {
        assertEquals("WRITE_DOCUMENTS", OracleDialect.INSTANCE.sqlName(SqlIdentifier.unquoted("write_documents")));
        assertEquals("\"size\"", OracleDialect.INSTANCE.sqlName(SqlIdentifier.parse("`size`")));

        assertEquals("write_documents", PostgresDialect.INSTANCE.sqlName(SqlIdentifier.unquoted("write_documents")));
        assertEquals("\"size\"", PostgresDialect.INSTANCE.sqlName(SqlIdentifier.parse("`size`")));

        assertEquals("write_documents", MysqlDialect.INSTANCE.sqlName(SqlIdentifier.unquoted("write_documents")));
        assertEquals("`size`", MysqlDialect.INSTANCE.sqlName(SqlIdentifier.parse("`size`")));

        assertEquals("write_documents", SqliteDialect.INSTANCE.sqlName(SqlIdentifier.unquoted("write_documents")));
        assertEquals("\"size\"", SqliteDialect.INSTANCE.sqlName(SqlIdentifier.parse("`size`")));
    }

    @Test
    void jdbcColumnLabelsFollowCatalogCase() {
        assertEquals("ID", OracleDialect.INSTANCE.jdbcColumnLabel(SqlIdentifier.unquoted("id")));
        assertEquals("size", OracleDialect.INSTANCE.jdbcColumnLabel(SqlIdentifier.parse("`size`")));

        assertEquals("id", PostgresDialect.INSTANCE.jdbcColumnLabel(SqlIdentifier.unquoted("id")));
        assertEquals("size", PostgresDialect.INSTANCE.jdbcColumnLabel(SqlIdentifier.parse("`size`")));
    }
}
