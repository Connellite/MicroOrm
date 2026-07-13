package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
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
        return new SqliteSchemaManager(new StorageDialect(SqliteDialect.getInstance(), storage));
    }

    private static PostgresSchemaManager postgres(UuidStorage storage) {
        return new PostgresSchemaManager(new StorageDialect(PostgresDialect.getInstance(), storage));
    }

    private static MysqlSchemaManager mysql(UuidStorage storage) {
        return new MysqlSchemaManager(new StorageDialect(MysqlDialect.getInstance(), storage));
    }

    private static MssqlSchemaManager mssql(UuidStorage storage) {
        return new MssqlSchemaManager(new StorageDialect(MssqlDialect.getInstance(), storage));
    }

    private static OracleSchemaManager oracle(UuidStorage storage) {
        return new OracleSchemaManager(new StorageDialect(OracleDialect.getInstance(), storage));
    }

    private record StorageDialect(Dialect delegate, UuidStorage storage) implements Dialect {
        @Override
        public String sqlName(SqlIdentifier identifier) {
            return delegate.sqlName(identifier);
        }

        @Override
        public String catalogName(SqlIdentifier identifier) {
            return delegate.catalogName(identifier);
        }

        @Override
        public String jdbcColumnLabel(SqlIdentifier identifier) {
            return delegate.jdbcColumnLabel(identifier);
        }

        @Override
        public SqlGenerator sqlGenerator() {
            return delegate.sqlGenerator();
        }

        @Override
        public JdbcValueMapper valueMapper() {
            return new DefaultJdbcValueMapper(storage);
        }

        @Override
        public void createTable(Connection c, EntityModel model) throws SQLException {
            delegate.createTable(c, model);
        }

        @Override
        public void syncTable(Connection c, EntityModel model) throws SQLException {
            delegate.syncTable(c, model);
        }

        @Override
        public void dropTable(Connection c, EntityModel model) throws SQLException {
            delegate.dropTable(c, model);
        }
    }
}
