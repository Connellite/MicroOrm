package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.SQLException;

class MssqlManyToManyWriteTest extends AbstractManyToManyWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DialectTestSupport.mssql().openConnection();
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.mssql(connection);
    }
}
