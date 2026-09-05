package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.SQLException;

class MysqlManyToManyWriteTest extends AbstractManyToManyWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DialectTestSupport.mysql().openConnection();
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.mysql(connection);
    }
}
