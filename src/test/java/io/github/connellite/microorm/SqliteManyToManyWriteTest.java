package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class SqliteManyToManyWriteTest extends AbstractManyToManyWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.sqlite(connection);
    }
}
