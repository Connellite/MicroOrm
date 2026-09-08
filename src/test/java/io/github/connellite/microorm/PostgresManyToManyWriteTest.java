package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.SQLException;

class PostgresManyToManyWriteTest extends AbstractManyToManyWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DialectTestSupport.postgres().openConnection();
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.postgres(connection);
    }
}
