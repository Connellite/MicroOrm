package io.github.connellite.microorm;

import org.testcontainers.containers.MSSQLServerContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class MssqlLazyRelationWriteTest extends AbstractLazyRelationWriteTest {

    private static final MSSQLServerContainer<?> MSSQL =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense();

    @Override
    protected Connection openConnection() throws SQLException {
        TestcontainersSupport.assumeDockerAvailable();
        if (!MSSQL.isRunning()) {
            MSSQL.start();
        }
        return DriverManager.getConnection(
                MSSQL.getJdbcUrl() + ";trustServerCertificate=true",
                MSSQL.getUsername(),
                MSSQL.getPassword());
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
