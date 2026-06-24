package io.github.connellite.microorm;

import org.testcontainers.containers.OracleContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OracleOrmContractTest extends AbstractOrmContractTest {

    private static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart");

    @Override
    protected Connection openConnection() throws SQLException {
        TestcontainersSupport.assumeDockerAvailable();
        if (!ORACLE.isRunning()) {
            ORACLE.start();
        }
        return DriverManager.getConnection(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword());
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.oracle(connection);
    }

    @Override
    protected String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    protected void assertUuidStorage(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT DATA_TYPE, DATA_LENGTH
                FROM USER_TAB_COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """)) {
            ps.setString(1, "contract_uuid_widgets");
            ps.setString(2, "id");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("UUID id column metadata was not found");
                }
                assertEquals("RAW", rs.getString("DATA_TYPE").toUpperCase());
                assertEquals(16, rs.getInt("DATA_LENGTH"));
            }
        }
    }
}
