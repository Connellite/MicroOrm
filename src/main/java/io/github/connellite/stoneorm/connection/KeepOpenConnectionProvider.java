package io.github.connellite.stoneorm.connection;

import java.sql.Connection;
import java.sql.SQLException;

/** Returns the same connection; does not close it on {@link #release(Connection)}. */
public final class KeepOpenConnectionProvider implements ConnectionProvider {

    private final Connection connection;

    public KeepOpenConnectionProvider(Connection connection) {
        this.connection = connection;
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
