package io.github.connellite.microorm.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Acquires and releases JDBC connections for {@link io.github.connellite.microorm.session.Session}.
 * <p>
 * Use {@link DataSourceConnectionProvider} with a pool, or {@link KeepOpenConnectionProvider}
 * when the caller owns a single long-lived connection (typical tests).
 */
public interface ConnectionProvider {

    /** Obtains a connection for a new session. */
    Connection acquire() throws SQLException;

    /** Returns or closes the connection when the session ends. */
    void release(Connection connection) throws SQLException;

    /**
     * Returns {@code true} when transaction completion is owned outside of the session,
     * for example by Spring.
     */
    default boolean isTransactionManaged(Connection connection) {
        return SpringJdbcSupport.isTransactionManagedConnection(connection);
    }
}
