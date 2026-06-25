package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Creates relation-write test tables with enforced foreign-key constraints (not via {@code createEntity}).
 */
final class RelationWriteFkSchema {

    enum Database {
        SQLITE,
        POSTGRES,
        MYSQL,
        MSSQL,
        ORACLE
    }

    private RelationWriteFkSchema() {
    }

    static void prepareConnection(Database database, Connection connection) throws SQLException {
        if (database == Database.SQLITE) {
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
    }

    static void recreateSchema(Database database, Connection connection) throws SQLException {
        dropSchema(database, connection);
        createSchema(database, connection);
    }

    static void dropSchema(Database database, Connection connection) throws SQLException {
        executeAll(connection, dropStatements(database));
    }

    static void createSchema(Database database, Connection connection) throws SQLException {
        executeAll(connection, createStatements(database));
    }

    private static void executeAll(Connection connection, List<String> statements) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    private static List<String> dropStatements(Database database) {
        return switch (database) {
            case SQLITE -> List.of(
                    "DROP TRIGGER IF EXISTS fk_write_doc_heads_primary_update",
                    "DROP TRIGGER IF EXISTS fk_write_doc_heads_primary_insert",
                    "DROP TABLE IF EXISTS write_head_files",
                    "DROP TABLE IF EXISTS write_doc_heads",
                    "DROP TABLE IF EXISTS write_files",
                    "DROP TABLE IF EXISTS write_documents");
            case POSTGRES -> List.of(
                    "DROP TABLE IF EXISTS write_head_files CASCADE",
                    "DROP TABLE IF EXISTS write_doc_heads CASCADE",
                    "DROP TABLE IF EXISTS write_files CASCADE",
                    "DROP TABLE IF EXISTS write_documents CASCADE");
            case MYSQL -> List.of(
                    "SET FOREIGN_KEY_CHECKS = 0",
                    "DROP TABLE IF EXISTS write_head_files",
                    "DROP TABLE IF EXISTS write_doc_heads",
                    "DROP TABLE IF EXISTS write_files",
                    "DROP TABLE IF EXISTS write_documents",
                    "SET FOREIGN_KEY_CHECKS = 1");
            case MSSQL -> List.of(
                    """
                    IF OBJECT_ID('fk_write_doc_heads_primary', 'F') IS NOT NULL
                        ALTER TABLE write_doc_heads DROP CONSTRAINT fk_write_doc_heads_primary
                    """,
                    """
                    IF OBJECT_ID('fk_write_head_files_head', 'F') IS NOT NULL
                        ALTER TABLE write_head_files DROP CONSTRAINT fk_write_head_files_head
                    """,
                    """
                    IF OBJECT_ID('fk_write_files_document', 'F') IS NOT NULL
                        ALTER TABLE write_files DROP CONSTRAINT fk_write_files_document
                    """,
                    "IF OBJECT_ID('write_head_files', 'U') IS NOT NULL DROP TABLE write_head_files",
                    "IF OBJECT_ID('write_doc_heads', 'U') IS NOT NULL DROP TABLE write_doc_heads",
                    "IF OBJECT_ID('write_files', 'U') IS NOT NULL DROP TABLE write_files",
                    "IF OBJECT_ID('write_documents', 'U') IS NOT NULL DROP TABLE write_documents");
            case ORACLE -> List.of(
                    oracleDropTable("write_head_files"),
                    oracleDropTable("write_doc_heads"),
                    oracleDropTable("write_files"),
                    oracleDropTable("write_documents"));
        };
    }

    private static String oracleDropTable(String table) {
        return """
                BEGIN
                    EXECUTE IMMEDIATE 'DROP TABLE "%s" CASCADE CONSTRAINTS PURGE';
                EXCEPTION
                    WHEN OTHERS THEN
                        IF SQLCODE != -942 THEN
                            RAISE;
                        END IF;
                END;
                """.formatted(table);
    }

    private static List<String> createStatements(Database database) {
        return switch (database) {
            case SQLITE -> sqliteCreate();
            case POSTGRES -> postgresCreate();
            case MYSQL -> mysqlCreate();
            case MSSQL -> mssqlCreate();
            case ORACLE -> oracleCreate();
        };
    }

    private static List<String> sqliteCreate() {
        return List.of(
                """
                CREATE TABLE write_documents (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL
                )
                """,
                """
                CREATE TABLE write_files (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    document_id TEXT NOT NULL,
                    CONSTRAINT fk_write_files_document
                        FOREIGN KEY (document_id) REFERENCES write_documents(id)
                )
                """,
                """
                CREATE TABLE write_doc_heads (
                    id TEXT NOT NULL PRIMARY KEY,
                    primary_file_id TEXT
                )
                """,
                """
                CREATE TABLE write_head_files (
                    id TEXT NOT NULL PRIMARY KEY,
                    label TEXT NOT NULL,
                    head_id TEXT,
                    CONSTRAINT fk_write_head_files_head
                        FOREIGN KEY (head_id) REFERENCES write_doc_heads(id)
                )
                """,
                """
                CREATE TRIGGER fk_write_doc_heads_primary_insert
                BEFORE INSERT ON write_doc_heads
                FOR EACH ROW
                WHEN NEW.primary_file_id IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM write_head_files WHERE id = NEW.primary_file_id)
                BEGIN
                    SELECT RAISE(ABORT, 'FK violation: write_doc_heads.primary_file_id');
                END
                """,
                """
                CREATE TRIGGER fk_write_doc_heads_primary_update
                BEFORE UPDATE OF primary_file_id ON write_doc_heads
                FOR EACH ROW
                WHEN NEW.primary_file_id IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM write_head_files WHERE id = NEW.primary_file_id)
                BEGIN
                    SELECT RAISE(ABORT, 'FK violation: write_doc_heads.primary_file_id');
                END
                """);
    }

    private static List<String> postgresCreate() {
        return List.of(
                """
                CREATE TABLE write_documents (
                    id UUID NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL
                )
                """,
                """
                CREATE TABLE write_files (
                    id UUID NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    document_id UUID NOT NULL,
                    CONSTRAINT fk_write_files_document
                        FOREIGN KEY (document_id) REFERENCES write_documents(id)
                )
                """,
                """
                CREATE TABLE write_doc_heads (
                    id UUID NOT NULL PRIMARY KEY,
                    primary_file_id UUID
                )
                """,
                """
                CREATE TABLE write_head_files (
                    id UUID NOT NULL PRIMARY KEY,
                    label TEXT NOT NULL,
                    head_id UUID,
                    CONSTRAINT fk_write_head_files_head
                        FOREIGN KEY (head_id) REFERENCES write_doc_heads(id)
                )
                """,
                """
                ALTER TABLE write_doc_heads
                    ADD CONSTRAINT fk_write_doc_heads_primary
                        FOREIGN KEY (primary_file_id) REFERENCES write_head_files(id)
                """);
    }

    private static List<String> mysqlCreate() {
        return List.of(
                """
                CREATE TABLE write_documents (
                    id BINARY(16) NOT NULL PRIMARY KEY,
                    title VARCHAR(255) NOT NULL
                ) ENGINE=InnoDB
                """,
                """
                CREATE TABLE write_files (
                    id BINARY(16) NOT NULL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    document_id BINARY(16) NOT NULL,
                    CONSTRAINT fk_write_files_document
                        FOREIGN KEY (document_id) REFERENCES write_documents(id)
                ) ENGINE=InnoDB
                """,
                """
                CREATE TABLE write_doc_heads (
                    id BINARY(16) NOT NULL PRIMARY KEY,
                    primary_file_id BINARY(16)
                ) ENGINE=InnoDB
                """,
                """
                CREATE TABLE write_head_files (
                    id BINARY(16) NOT NULL PRIMARY KEY,
                    label VARCHAR(255) NOT NULL,
                    head_id BINARY(16),
                    CONSTRAINT fk_write_head_files_head
                        FOREIGN KEY (head_id) REFERENCES write_doc_heads(id)
                ) ENGINE=InnoDB
                """,
                """
                ALTER TABLE write_doc_heads
                    ADD CONSTRAINT fk_write_doc_heads_primary
                        FOREIGN KEY (primary_file_id) REFERENCES write_head_files(id)
                """);
    }

    private static List<String> mssqlCreate() {
        return List.of(
                """
                CREATE TABLE write_documents (
                    id BINARY(16) NOT NULL PRIMARY KEY,
                    title NVARCHAR(255) NOT NULL
                )
                """,
                """
                CREATE TABLE write_files (
                    id BINARY(16) NOT NULL PRIMARY KEY,
                    name NVARCHAR(255) NOT NULL,
                    document_id BINARY(16) NOT NULL,
                    CONSTRAINT fk_write_files_document
                        FOREIGN KEY (document_id) REFERENCES write_documents(id)
                )
                """,
                """
                CREATE TABLE write_doc_heads (
                    id BINARY(16) NOT NULL PRIMARY KEY,
                    primary_file_id BINARY(16)
                )
                """,
                """
                CREATE TABLE write_head_files (
                    id BINARY(16) NOT NULL PRIMARY KEY,
                    label NVARCHAR(255) NOT NULL,
                    head_id BINARY(16),
                    CONSTRAINT fk_write_head_files_head
                        FOREIGN KEY (head_id) REFERENCES write_doc_heads(id)
                )
                """,
                """
                ALTER TABLE write_doc_heads
                    ADD CONSTRAINT fk_write_doc_heads_primary
                        FOREIGN KEY (primary_file_id) REFERENCES write_head_files(id)
                """);
    }

    private static List<String> oracleCreate() {
        return List.of(
                """
                CREATE TABLE "write_documents" (
                    "id" RAW(16) NOT NULL PRIMARY KEY,
                    "title" VARCHAR2(255) NOT NULL
                )
                """,
                """
                CREATE TABLE "write_files" (
                    "id" RAW(16) NOT NULL PRIMARY KEY,
                    "name" VARCHAR2(255) NOT NULL,
                    "document_id" RAW(16) NOT NULL,
                    CONSTRAINT fk_write_files_document
                        FOREIGN KEY ("document_id") REFERENCES "write_documents"("id")
                )
                """,
                """
                CREATE TABLE "write_doc_heads" (
                    "id" RAW(16) NOT NULL PRIMARY KEY,
                    "primary_file_id" RAW(16)
                )
                """,
                """
                CREATE TABLE "write_head_files" (
                    "id" RAW(16) NOT NULL PRIMARY KEY,
                    "label" VARCHAR2(255) NOT NULL,
                    "head_id" RAW(16),
                    CONSTRAINT fk_write_head_files_head
                        FOREIGN KEY ("head_id") REFERENCES "write_doc_heads"("id")
                )
                """,
                """
                ALTER TABLE "write_doc_heads"
                    ADD CONSTRAINT fk_write_doc_heads_primary
                        FOREIGN KEY ("primary_file_id") REFERENCES "write_head_files"("id")
                """);
    }
}
