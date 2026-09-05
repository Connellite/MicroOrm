package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.SQLException;

class MysqlLazyRelationWriteTest extends AbstractLazyRelationWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DialectTestSupport.mysql().openConnection();
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.mysql(connection);
    }

    @Override
    protected RelationWriteFkSchema.Database databaseKind() {
        return RelationWriteFkSchema.Database.MYSQL;
    }
}
