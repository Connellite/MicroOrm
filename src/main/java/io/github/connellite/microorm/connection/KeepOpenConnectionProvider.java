package io.github.connellite.microorm.connection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Returns the same connection; does not close it on {@link #release(Connection)}. */
public final class KeepOpenConnectionProvider implements ConnectionProvider {

    private final Connection connection;

    /** Wraps a caller-owned connection; {@link #release(Connection)} is a no-op. */
    public KeepOpenConnectionProvider(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public Connection acquire() {
        return connection;
    }

    @Override
    public void release(Connection connection) throws SQLException {
        // lifecycle owned by caller
    }
}
