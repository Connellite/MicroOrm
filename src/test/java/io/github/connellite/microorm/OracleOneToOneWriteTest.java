package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.SQLException;

class OracleOneToOneWriteTest extends AbstractOneToOneWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DialectTestSupport.oracle().openConnection();
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.oracle(connection);
    }
}
