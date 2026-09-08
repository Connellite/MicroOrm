package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.SQLException;

class OracleLazyRelationWriteTest extends AbstractLazyRelationWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DialectTestSupport.oracle().openConnection();
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.oracle(connection);
    }

    @Override
    protected RelationWriteFkSchema.Database databaseKind() {
        return RelationWriteFkSchema.Database.ORACLE;
    }
}
