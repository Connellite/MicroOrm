package io.github.connellite.microorm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class SqliteLazyRelationWriteTest extends AbstractLazyRelationWriteTest {

    @Override
    protected Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @Override
    protected MicroOrm createOrm(Connection connection) {
        return MicroOrm.sqlite(connection);
    }

    @Override
    protected RelationWriteFkSchema.Database databaseKind() {
        return RelationWriteFkSchema.Database.SQLITE;
    }
}
