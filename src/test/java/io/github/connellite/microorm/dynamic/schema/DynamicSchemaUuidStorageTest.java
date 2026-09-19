package io.github.connellite.microorm.dynamic.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.dynamic.DynamicDialectSupport;
import io.github.connellite.microorm.dynamic.LogicalType;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicSchemaUuidStorageTest {

    @Test
    void sqliteUuidTypeFollowsUuidStorage() {
        assertEquals("TEXT", sqlite(UuidStorage.STRING).baseTypeForLogical(LogicalType.UUID, 0));
        assertEquals("BLOB", sqlite(UuidStorage.BINARY).baseTypeForLogical(LogicalType.UUID, 0));
        assertThrows(IllegalArgumentException.class,
                () -> sqlite(UuidStorage.MICROSOFT_GUID).baseTypeForLogical(LogicalType.UUID, 0));
    }

    @Test
    void postgresUuidTypeFollowsUuidStorage() {
        assertEquals("UUID", postgres(UuidStorage.NATIVE).baseTypeForLogical(LogicalType.UUID, 0));
        assertEquals("BYTEA", postgres(UuidStorage.BINARY).baseTypeForLogical(LogicalType.UUID, 0));
        assertThrows(IllegalArgumentException.class,
                () -> postgres(UuidStorage.MICROSOFT_GUID).baseTypeForLogical(LogicalType.UUID, 0));
        assertEquals("TEXT", postgres(UuidStorage.STRING).baseTypeForLogical(LogicalType.UUID, 0));
    }

    @Test
    void mysqlUuidTypeFollowsUuidStorage() {
        assertEquals("BINARY(16)", mysql(UuidStorage.BINARY).baseTypeForLogical(LogicalType.UUID, 0));
        assertThrows(IllegalArgumentException.class,
                () -> mysql(UuidStorage.MICROSOFT_GUID).baseTypeForLogical(LogicalType.UUID, 0));
        assertEquals("CHAR(36)", mysql(UuidStorage.STRING).baseTypeForLogical(LogicalType.UUID, 0));
    }

    @Test
    void mssqlUuidTypeFollowsUuidStorage() {
        assertEquals("BINARY(16)", mssql(UuidStorage.BINARY).baseTypeForLogical(LogicalType.UUID, 0));
        assertEquals("UNIQUEIDENTIFIER", mssql(UuidStorage.MICROSOFT_GUID).baseTypeForLogical(LogicalType.UUID, 0));
        assertEquals("UNIQUEIDENTIFIER", mssql(UuidStorage.NATIVE).baseTypeForLogical(LogicalType.UUID, 0));
        assertEquals("NVARCHAR(36)", mssql(UuidStorage.STRING).baseTypeForLogical(LogicalType.UUID, 0));
    }

    @Test
    void oracleUuidTypeFollowsUuidStorage() {
        assertEquals("RAW(16)", oracle(UuidStorage.BINARY).baseTypeForLogical(LogicalType.UUID, 0));
        assertThrows(IllegalArgumentException.class,
                () -> oracle(UuidStorage.MICROSOFT_GUID).baseTypeForLogical(LogicalType.UUID, 0));
        assertEquals("VARCHAR2(36)", oracle(UuidStorage.STRING).baseTypeForLogical(LogicalType.UUID, 0));
    }

    private static SqliteDynamicSchemaManager sqlite(UuidStorage storage) {
        return (SqliteDynamicSchemaManager) DynamicDialectSupport.schemaManager(withStorage(SqliteDialect.getInstance(), storage));
    }

    private static PostgresDynamicSchemaManager postgres(UuidStorage storage) {
        return (PostgresDynamicSchemaManager) DynamicDialectSupport.schemaManager(withStorage(PostgresDialect.getInstance(), storage));
    }

    private static MysqlDynamicSchemaManager mysql(UuidStorage storage) {
        return (MysqlDynamicSchemaManager) DynamicDialectSupport.schemaManager(withStorage(MysqlDialect.getInstance(), storage));
    }

    private static MssqlDynamicSchemaManager mssql(UuidStorage storage) {
        return (MssqlDynamicSchemaManager) DynamicDialectSupport.schemaManager(withStorage(MssqlDialect.getInstance(), storage));
    }

    private static OracleDynamicSchemaManager oracle(UuidStorage storage) {
        return (OracleDynamicSchemaManager) DynamicDialectSupport.schemaManager(withStorage(OracleDialect.getInstance(), storage));
    }

    private static Dialect withStorage(Dialect dialect, UuidStorage storage) {
        return dialect.withValueMapper(new DefaultJdbcValueMapper(storage));
    }
}
