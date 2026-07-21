package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleOrmContractTest extends AbstractOrmContractTest {

    private static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart");

    @Entity
    @Table(name = "contract_oracle_temporal")
    public static class OracleTemporalRow {
        @Id
        private long id;

        @Column(name = "created_at")
        private LocalDateTime createdAt;
    }

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
        return identifier.toUpperCase();
    }

    @Override
    protected void assertUuidStorage(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT DATA_TYPE, DATA_LENGTH
                FROM USER_TAB_COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """)) {
            ps.setString(1, "CONTRACT_UUID_WIDGETS");
            ps.setString(2, "ID");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("UUID id column metadata was not found");
                }
                assertEquals("RAW", rs.getString("DATA_TYPE").toUpperCase());
                assertEquals(16, rs.getInt("DATA_LENGTH"));
            }
        }
    }

    @Test
    void mapsOracleTimestampWithLocalTimeZoneFromRealJdbcDriver() throws SQLException {
        try (Connection connection = openConnection()) {
            setOracleSessionTimeZone(connection);
            dropTemporalTable(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE CONTRACT_ORACLE_TEMPORAL (
                            ID NUMBER(19) NOT NULL PRIMARY KEY,
                            CREATED_AT TIMESTAMP WITH LOCAL TIME ZONE NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO CONTRACT_ORACLE_TEMPORAL (ID, CREATED_AT)
                        VALUES (1, TIMESTAMP '2026-07-13 21:50:45')
                        """);
            }

            LocalDateTime expected;
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT CREATED_AT
                    FROM CONTRACT_ORACLE_TEMPORAL
                    WHERE ID = 1
                    """);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("oracle.sql.TIMESTAMPLTZ", rs.getObject("CREATED_AT").getClass().getName());
                expected = rs.getTimestamp("CREATED_AT").toLocalDateTime();
            }

            MicroOrm orm = createOrm(connection).register(OracleTemporalRow.class);
            try (Session session = orm.openSession()) {
                OracleTemporalRow row = session.selectRow(OracleTemporalRow.class, 1L);

                assertEquals(1L, row.id);
                assertEquals(expected, row.createdAt);
            } finally {
                dropTemporalTable(connection);
            }
        }
    }

    private static void setOracleSessionTimeZone(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER SESSION SET TIME_ZONE = '+00:00'");
        }
    }

    private static void dropTemporalTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE CONTRACT_ORACLE_TEMPORAL PURGE");
        } catch (SQLException e) {
            if (e.getErrorCode() != 942) {
                throw e;
            }
        }
    }
}
