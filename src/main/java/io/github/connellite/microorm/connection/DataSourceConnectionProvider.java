package io.github.connellite.microorm.connection;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class DataSourceConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    public DataSourceConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection acquire() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void release(Connection connection) throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
