package io.github.connellite.microorm.dynamic.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.dynamic.LogicalType;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

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
        return new SqliteDynamicSchemaManager(new StorageDialect(SqliteDialect.getInstance(), storage));
    }

    private static PostgresDynamicSchemaManager postgres(UuidStorage storage) {
        return new PostgresDynamicSchemaManager(new StorageDialect(PostgresDialect.getInstance(), storage));
    }

    private static MysqlDynamicSchemaManager mysql(UuidStorage storage) {
        return new MysqlDynamicSchemaManager(new StorageDialect(MysqlDialect.getInstance(), storage));
    }

    private static MssqlDynamicSchemaManager mssql(UuidStorage storage) {
        return new MssqlDynamicSchemaManager(new StorageDialect(MssqlDialect.getInstance(), storage));
    }

    private static OracleDynamicSchemaManager oracle(UuidStorage storage) {
        return new OracleDynamicSchemaManager(new StorageDialect(OracleDialect.getInstance(), storage));
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
