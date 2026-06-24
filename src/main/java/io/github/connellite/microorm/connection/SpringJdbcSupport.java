package io.github.connellite.microorm.connection;

import java.sql.Connection;

/**
 * Detects Spring {@code TransactionAwareDataSourceProxy} connections without a compile-time Spring dependency.
 */
public final class SpringJdbcSupport {

    private static final String CONNECTION_PROXY = "org.springframework.jdbc.datasource.ConnectionProxy";

    private SpringJdbcSupport() {
    }

    public static boolean isTransactionManagedConnection(Connection connection) {
        if (connection == null) {
            return false;
        }
        Class<?> type = connection.getClass();
        while (type != null) {
            if (CONNECTION_PROXY.equals(type.getName())) {
                return true;
            }
            for (Class<?> iface : type.getInterfaces()) {
                if (CONNECTION_PROXY.equals(iface.getName())) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
