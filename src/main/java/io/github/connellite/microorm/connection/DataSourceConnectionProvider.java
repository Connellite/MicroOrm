package io.github.connellite.microorm.connection;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** {@link ConnectionProvider} that delegates to {@link javax.sql.DataSource#getConnection()}. */
public final class DataSourceConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    /** Wraps a {@link DataSource}; {@link #release(Connection)} closes the connection. */
    public DataSourceConnectionProvider(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
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
