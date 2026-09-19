package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaUuidStorageTest {

    @Test
    void sqliteUuidTypeRejectsMicrosoftGuidStorage() {
        assertEquals("TEXT", sqlite(UuidStorage.STRING).baseTypeForJava(UUID.class, 0));
        assertEquals("BLOB", sqlite(UuidStorage.BINARY).baseTypeForJava(UUID.class, 0));
        assertThrows(IllegalArgumentException.class,
                () -> sqlite(UuidStorage.MICROSOFT_GUID).baseTypeForJava(UUID.class, 0));
    }

    @Test
    void postgresUuidTypeRejectsMicrosoftGuidStorage() {
        assertEquals("UUID", postgres(UuidStorage.NATIVE).baseTypeForJava(UUID.class, 0));
        assertEquals("BYTEA", postgres(UuidStorage.BINARY).baseTypeForJava(UUID.class, 0));
        assertThrows(IllegalArgumentException.class,
                () -> postgres(UuidStorage.MICROSOFT_GUID).baseTypeForJava(UUID.class, 0));
        assertEquals("TEXT", postgres(UuidStorage.STRING).baseTypeForJava(UUID.class, 0));
    }

    @Test
    void mysqlUuidTypeRejectsMicrosoftGuidStorage() {
        assertEquals("BINARY(16)", mysql(UuidStorage.BINARY).baseTypeForJava(UUID.class, 0));
        assertThrows(IllegalArgumentException.class,
                () -> mysql(UuidStorage.MICROSOFT_GUID).baseTypeForJava(UUID.class, 0));
        assertEquals("CHAR(36)", mysql(UuidStorage.STRING).baseTypeForJava(UUID.class, 0));
    }

    @Test
    void mssqlUuidTypeAllowsMicrosoftGuidStorage() {
        assertEquals("BINARY(16)", mssql(UuidStorage.BINARY).baseTypeForJava(UUID.class, 0));
        assertEquals("UNIQUEIDENTIFIER", mssql(UuidStorage.MICROSOFT_GUID).baseTypeForJava(UUID.class, 0));
        assertEquals("UNIQUEIDENTIFIER", mssql(UuidStorage.NATIVE).baseTypeForJava(UUID.class, 0));
        assertEquals("NVARCHAR(36)", mssql(UuidStorage.STRING).baseTypeForJava(UUID.class, 0));
    }

    @Test
    void oracleUuidTypeRejectsMicrosoftGuidStorage() {
        assertEquals("RAW(16)", oracle(UuidStorage.BINARY).baseTypeForJava(UUID.class, 0));
        assertThrows(IllegalArgumentException.class,
                () -> oracle(UuidStorage.MICROSOFT_GUID).baseTypeForJava(UUID.class, 0));
        assertEquals("VARCHAR2(36)", oracle(UuidStorage.STRING).baseTypeForJava(UUID.class, 0));
    }

    private static SqliteSchemaManager sqlite(UuidStorage storage) {
        return (SqliteSchemaManager) withStorage(SqliteDialect.getInstance(), storage).schemaManager();
    }

    private static PostgresSchemaManager postgres(UuidStorage storage) {
        return (PostgresSchemaManager) withStorage(PostgresDialect.getInstance(), storage).schemaManager();
    }

    private static MysqlSchemaManager mysql(UuidStorage storage) {
        return (MysqlSchemaManager) withStorage(MysqlDialect.getInstance(), storage).schemaManager();
    }

    private static MssqlSchemaManager mssql(UuidStorage storage) {
        return (MssqlSchemaManager) withStorage(MssqlDialect.getInstance(), storage).schemaManager();
    }

    private static OracleSchemaManager oracle(UuidStorage storage) {
        return (OracleSchemaManager) withStorage(OracleDialect.getInstance(), storage).schemaManager();
    }

    private static Dialect withStorage(Dialect dialect, UuidStorage storage) {
        return dialect.withValueMapper(new DefaultJdbcValueMapper(storage));
    }
}
