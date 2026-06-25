package io.github.connellite.microorm;

import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class MysqlLazyRelationWriteTest extends AbstractLazyRelationWriteTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Override
    protected Connection openConnection() throws SQLException {
        TestcontainersSupport.assumeDockerAvailable();
        if (!MYSQL.isRunning()) {
            MYSQL.start();
        }
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
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
