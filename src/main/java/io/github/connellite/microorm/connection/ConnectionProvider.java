package io.github.connellite.microorm.connection;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionProvider {

    Connection acquire() throws SQLException;

    void release(Connection connection) throws SQLException;
}
