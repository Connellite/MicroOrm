package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.SQLException;

class MssqlLazyRelationWriteTest extends AbstractLazyRelationWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DialectTestSupport.mssql().openConnection();
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.mssql(connection);
    }

    @Override
    protected RelationWriteFkSchema.Database databaseKind() {
        return RelationWriteFkSchema.Database.MSSQL;
    }
}
