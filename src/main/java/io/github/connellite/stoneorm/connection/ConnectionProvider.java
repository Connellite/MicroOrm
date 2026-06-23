package io.github.connellite.stoneorm.connection;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionProvider {

    Connection acquire() throws SQLException;

    void release(Connection connection) throws SQLException;
}
