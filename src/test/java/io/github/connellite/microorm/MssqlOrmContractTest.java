package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.connection.KeepOpenConnectionProvider;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.schema.MssqlSchemaManager;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.MssqlSqlGenerator;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;
import io.github.connellite.util.UuidUtil;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MssqlOrmContractTest extends AbstractOrmContractTest {

    private static final MSSQLServerContainer<?> MSSQL =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense();

    @Override
    protected Connection openConnection() throws SQLException {
        TestcontainersSupport.assumeDockerAvailable();
        if (!MSSQL.isRunning()) {
            MSSQL.start();
        }
        return DriverManager.getConnection(
                MSSQL.getJdbcUrl() + ";trustServerCertificate=true",
                MSSQL.getUsername(),
                MSSQL.getPassword());
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.mssql(connection);
    }

    @Override
    protected String quote(String identifier) {
        return identifier;
    }

    @Override
    protected void assertUuidStorage(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """)) {
            ps.setString(1, "contract_uuid_widgets");
            ps.setString(2, "id");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("UUID id column metadata was not found");
                }
                assertEquals("binary", rs.getString("DATA_TYPE").toLowerCase());
                assertEquals(16, rs.getInt("CHARACTER_MAXIMUM_LENGTH"));
            }
        }
    }

    @Entity
    @Table(name = "mssql_precreated_guid_widgets")
    static class PrecreatedGuidWidget {
        @Id
        private UUID id;

        @Column(nullable = false)
        private String name;

        PrecreatedGuidWidget() {
        }
    }

    @Test
    void microsoftGuidStorageWorksWithPrecreatedMssqlBinaryTable() throws SQLException {
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS mssql_precreated_guid_widgets");
                statement.execute("""
                        CREATE TABLE mssql_precreated_guid_widgets (
                            id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
                            name NVARCHAR(64) NOT NULL
                        )
                        """);
            }
            assertColumnType(connection, "mssql_precreated_guid_widgets", "id", "uniqueidentifier");

            try {
                UUID externalId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO mssql_precreated_guid_widgets (id, name) VALUES (?, ?)")) {
                    ps.setBytes(1, UuidUtil.uuid2MicrosoftGuidBinary(externalId));
                    ps.setString(2, "external");
                    ps.executeUpdate();
                }

                MicroOrm orm = new MicroOrm(
                        new MssqlMicrosoftGuidDialect(),
                        new KeepOpenConnectionProvider(connection),
                        new EntityModelRegistry()).register(PrecreatedGuidWidget.class);

                try (Session session = orm.openSession()) {
                    PrecreatedGuidWidget loaded = session.selectRow(PrecreatedGuidWidget.class, externalId);
                    assertNotNull(loaded);
                    assertEquals("external", loaded.name);

                    PrecreatedGuidWidget inserted = new PrecreatedGuidWidget();
                    inserted.id = UUID.fromString("11111111-2222-3333-4444-555555555555");
                    inserted.name = "orm";
                    session.insertRow(inserted);

                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT id FROM mssql_precreated_guid_widgets WHERE name = ?")) {
                        ps.setString(1, "orm");
                        try (ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next());
                            assertArrayEquals(UuidUtil.uuid2MicrosoftGuidBinary(inserted.id), rs.getBytes(1));
                        }
                    }
                }
            } finally {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TABLE IF EXISTS mssql_precreated_guid_widgets");
                }
            }
        }
    }

    @Test
    void microsoftGuidSchemaManagerCreatesUniqueidentifierColumn() throws SQLException {
        try (Connection connection = openConnection()) {
            MicroOrm orm = new MicroOrm(
                    new MssqlMicrosoftGuidDialect(),
                    new KeepOpenConnectionProvider(connection),
                    new EntityModelRegistry()).register(PrecreatedGuidWidget.class);

            try (Session session = orm.openSession()) {
                session.dropEntity(PrecreatedGuidWidget.class);
                session.createEntity(PrecreatedGuidWidget.class);

                assertColumnType(connection, "mssql_precreated_guid_widgets", "id", "uniqueidentifier");
            } finally {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TABLE IF EXISTS mssql_precreated_guid_widgets");
                }
            }
        }
    }

    private static void assertColumnType(
            Connection connection,
            String tableName,
            String columnName,
            String expectedDataType) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT DATA_TYPE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expectedDataType, rs.getString("DATA_TYPE").toLowerCase());
            }
        }
    }

    private static final class MssqlMicrosoftGuidDialect implements Dialect {
        private final Dialect delegate = MssqlDialect.getInstance();
        private final JdbcValueMapper valueMapper = new DefaultJdbcValueMapper(UuidStorage.MICROSOFT_GUID);
        private final SqlGenerator sqlGenerator = new MssqlSqlGenerator(this);
        private final SchemaManager schemaManager = new MssqlSchemaManager(this);

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
            return sqlGenerator;
        }

        @Override
        public JdbcValueMapper valueMapper() {
            return valueMapper;
        }

        @Override
        public void createTable(Connection c, EntityModel model) throws SQLException {
            schemaManager.createTable(c, model);
        }

        @Override
        public void syncTable(Connection c, EntityModel model) throws SQLException {
            schemaManager.syncTable(c, model);
        }

        @Override
        public void dropTable(Connection c, EntityModel model) throws SQLException {
            schemaManager.dropTable(c, model);
        }
    }
}
