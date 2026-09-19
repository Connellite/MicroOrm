package io.github.connellite.microorm.session;

import io.github.connellite.microorm.sql.Query;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC connection, local transactions, native SQL, and connection release implemented by {@link Session}.
 */
public interface JdbcSession extends AutoCloseable {

    Connection connection();

    void beginTransaction() throws SQLException;

    void commitTransaction() throws SQLException;

    void rollbackTransaction() throws SQLException;

    int execute(Query query);

    <T> T selectScalar(Query query, Class<T> targetType);

    @Override
    void close() throws SQLException;
}
