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
        assertEquals("WRITE_DOCUMENTS", OracleDialect.getInstance().sqlName(SqlIdentifier.unquoted("write_documents")));
        assertEquals("\"size\"", OracleDialect.getInstance().sqlName(SqlIdentifier.parse("`size`")));

        assertEquals("write_documents", PostgresDialect.getInstance().sqlName(SqlIdentifier.unquoted("write_documents")));
        assertEquals("\"size\"", PostgresDialect.getInstance().sqlName(SqlIdentifier.parse("`size`")));

        assertEquals("write_documents", MysqlDialect.getInstance().sqlName(SqlIdentifier.unquoted("write_documents")));
        assertEquals("`size`", MysqlDialect.getInstance().sqlName(SqlIdentifier.parse("`size`")));

        assertEquals("write_documents", SqliteDialect.getInstance().sqlName(SqlIdentifier.unquoted("write_documents")));
        assertEquals("\"size\"", SqliteDialect.getInstance().sqlName(SqlIdentifier.parse("`size`")));
    }

    @Test
    void jdbcColumnLabelsFollowCatalogCase() {
        assertEquals("ID", OracleDialect.getInstance().jdbcColumnLabel(SqlIdentifier.unquoted("id")));
        assertEquals("size", OracleDialect.getInstance().jdbcColumnLabel(SqlIdentifier.parse("`size`")));

        assertEquals("id", PostgresDialect.getInstance().jdbcColumnLabel(SqlIdentifier.unquoted("id")));
        assertEquals("size", PostgresDialect.getInstance().jdbcColumnLabel(SqlIdentifier.parse("`size`")));
    }
}
