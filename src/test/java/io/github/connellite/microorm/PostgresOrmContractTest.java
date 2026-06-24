package io.github.connellite.microorm;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgresOrmContractTest extends AbstractOrmContractTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    protected Connection openConnection() throws SQLException {
        TestcontainersSupport.assumeDockerAvailable();
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.postgres(connection);
    }

    @Override
    protected String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    protected void assertUuidStorage(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """)) {
            ps.setString(1, "contract_uuid_widgets");
            ps.setString(2, "id");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("UUID id column metadata was not found");
                }
                assertEquals("uuid", rs.getString("data_type"));
            }
        }
    }
}
