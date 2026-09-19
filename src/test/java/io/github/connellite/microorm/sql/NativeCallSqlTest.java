package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeCallSqlTest {

    @Test
    void defaultProcedureSqlUsesCall() {
        assertEquals(
                "CALL archive_inactive_users()",
                SqliteDialect.getInstance().sqlGenerator().procedureSql("archive_inactive_users", List.of()));
        assertEquals(
                "CALL archive_inactive_users(:name)",
                PostgresDialect.getInstance().sqlGenerator().procedureSql("archive_inactive_users", List.of(":name")));
    }

    @Test
    void defaultFunctionSqlUsesSelect() {
        assertEquals(
                "SELECT count_active_users()",
                SqliteDialect.getInstance().sqlGenerator().functionSql("count_active_users", List.of()));
        assertEquals(
                "SELECT count_active_users(:name)",
                PostgresDialect.getInstance().sqlGenerator().functionSql("count_active_users", List.of(":name")));
    }

    @Test
    void mssqlProcedureSqlUsesExec() {
        SqlGenerator sql = MssqlDialect.getInstance().sqlGenerator();
        assertEquals("EXEC archive_inactive_users", sql.procedureSql("archive_inactive_users", List.of()));
        assertEquals("EXEC archive_inactive_users :name", sql.procedureSql("archive_inactive_users", List.of(":name")));
    }

    @Test
    void mssqlFunctionSqlUsesDboSelect() {
        SqlGenerator sql = MssqlDialect.getInstance().sqlGenerator();
        assertEquals("SELECT dbo.count_active_users()", sql.functionSql("count_active_users", List.of()));
        assertEquals("SELECT dbo.item_label(:name)", sql.functionSql("item_label", List.of(":name")));
    }

    @Test
    void oracleFunctionSqlUsesDual() {
        SqlGenerator sql = OracleDialect.getInstance().sqlGenerator();
        assertEquals("SELECT count_active_users() FROM dual", sql.functionSql("count_active_users", List.of()));
        assertEquals("SELECT item_label(:name) FROM dual", sql.functionSql("item_label", List.of(":name")));
    }

    @Test
    void nativeSqlIsPassedThrough() {
        SqlGenerator mssql = MssqlDialect.getInstance().sqlGenerator();
        SqlGenerator oracle = OracleDialect.getInstance().sqlGenerator();
        assertEquals(
                "EXEC archive_inactive_users @p_name = :name",
                mssql.procedureSql("EXEC archive_inactive_users @p_name = :name", List.of(":name")));
        assertEquals(
                "SELECT dbo.item_label(:name)",
                mssql.functionSql("SELECT dbo.item_label(:name)", List.of(":name")));
        assertEquals(
                "SELECT item_label(:name) FROM dual",
                oracle.functionSql("SELECT item_label(:name) FROM dual", List.of(":name")));
        assertEquals(
                "BEGIN archive_inactive_users(:name); END;",
                oracle.procedureSql("BEGIN archive_inactive_users(:name); END;", List.of(":name")));
    }

    @Test
    void looksLikeNativeSqlDetectsCallForms() {
        SqlGenerator sql = SqliteDialect.getInstance().sqlGenerator();
        assertTrue(sql.looksLikeNativeSql("CALL archive()"));
        assertTrue(sql.looksLikeNativeSql("select dbo.fn()"));
        assertTrue(sql.looksLikeNativeSql("{call archive(:name)}"));
        assertTrue(sql.looksLikeNativeSql("BEGIN archive(:name); END;"));
        assertFalse(sql.looksLikeNativeSql("archive_inactive_users"));
        assertFalse(sql.looksLikeNativeSql("beginner_archive"));
    }
}
