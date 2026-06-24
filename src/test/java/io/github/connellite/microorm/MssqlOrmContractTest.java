package io.github.connellite.microorm;

import org.testcontainers.containers.MSSQLServerContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    protected Orm createOrm(Connection connection) {
        return Orm.mssql(connection);
    }

    @Override
    protected String quote(String identifier) {
        return "[" + identifier + "]";
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
}
