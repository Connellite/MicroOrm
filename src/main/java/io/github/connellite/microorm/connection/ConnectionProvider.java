package io.github.connellite.microorm.connection;

import java.sql.Connection;
import java.sql.SQLException;

/** Acquires and releases JDBC connections for {@link io.github.connellite.microorm.session.Session}. */
public interface ConnectionProvider {

    Connection acquire() throws SQLException;

    void release(Connection connection) throws SQLException;
}
