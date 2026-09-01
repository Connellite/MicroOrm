package io.github.connellite.microorm.connection;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** {@link ConnectionProvider} that delegates to a {@link javax.sql.DataSource}. */
public final class DataSourceConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    /** Wraps a {@link DataSource}; {@link #release(Connection)} closes the connection. */
    public DataSourceConnectionProvider(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Connection acquire() throws SQLException {
        return SpringJdbcSupport.getConnection(dataSource);
    }

    @Override
    public void release(Connection connection) throws SQLException {
        SpringJdbcSupport.releaseConnection(connection, dataSource);
    }

    @Override
    public boolean isTransactionManaged(Connection connection) {
        return SpringJdbcSupport.isTransactionManagedConnection(connection)
                || (SpringJdbcSupport.isActualTransactionActive()
                && SpringJdbcSupport.isConnectionTransactional(connection, dataSource));
    }
}
