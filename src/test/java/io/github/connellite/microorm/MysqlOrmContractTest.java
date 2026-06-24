package io.github.connellite.microorm;

import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MysqlOrmContractTest extends AbstractOrmContractTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Override
    protected Connection openConnection() throws SQLException {
        TestcontainersSupport.assumeDockerAvailable();
        if (!MYSQL.isRunning()) {
            MYSQL.start();
        }
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.mysql(connection);
    }

    @Override
    protected String quote(String identifier) {
        return "`" + identifier + "`";
    }

    @Override
    protected void assertUuidStorage(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT data_type, character_octet_length
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """)) {
            ps.setString(1, "contract_uuid_widgets");
            ps.setString(2, "id");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("UUID id column metadata was not found");
                }
                assertEquals("binary", rs.getString("data_type").toLowerCase());
                assertEquals(16, rs.getInt("character_octet_length"));
            }
        }
    }
}
