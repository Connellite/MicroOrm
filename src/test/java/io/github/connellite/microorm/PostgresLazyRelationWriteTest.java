package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.SQLException;

class PostgresLazyRelationWriteTest extends AbstractLazyRelationWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DialectTestSupport.postgres().openConnection();
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.postgres(connection);
    }

    @Override
    protected RelationWriteFkSchema.Database databaseKind() {
        return RelationWriteFkSchema.Database.POSTGRES;
    }
}
