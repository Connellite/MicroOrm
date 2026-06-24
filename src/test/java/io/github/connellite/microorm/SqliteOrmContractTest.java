package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteOrmContractTest extends AbstractOrmContractTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @Override
    protected Orm createOrm(Connection connection) {
        return Orm.sqlite(connection);
    }

    @Override
    protected String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    protected void assertUuidStorage(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(\"contract_uuid_widgets\")")) {
            while (rs.next()) {
                if ("id".equals(rs.getString("name"))) {
                    assertEquals("TEXT", rs.getString("type").toUpperCase());
                    return;
                }
            }
        }
        throw new AssertionError("UUID id column metadata was not found");
    }
}
