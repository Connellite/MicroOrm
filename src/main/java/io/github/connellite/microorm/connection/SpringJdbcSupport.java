package io.github.connellite.microorm.connection;

import lombok.experimental.UtilityClass;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Integrates with Spring JDBC when it is present, without a compile-time Spring dependency.
 */
@UtilityClass
public final class SpringJdbcSupport {

    private static final String DATA_SOURCE_UTILS = "org.springframework.jdbc.datasource.DataSourceUtils";
    private static final String CONNECTION_PROXY = "org.springframework.jdbc.datasource.ConnectionProxy";
    private static final String TRANSACTION_SYNCHRONIZATION_MANAGER =
            "org.springframework.transaction.support.TransactionSynchronizationManager";

    private static final Method GET_CONNECTION = findMethod(DATA_SOURCE_UTILS, "getConnection", DataSource.class);
    private static final Method RELEASE_CONNECTION =
            findMethod(DATA_SOURCE_UTILS, "releaseConnection", Connection.class, DataSource.class);
    private static final Method IS_CONNECTION_TRANSACTIONAL =
            findMethod(DATA_SOURCE_UTILS, "isConnectionTransactional", Connection.class, DataSource.class);
    private static final Method IS_ACTUAL_TRANSACTION_ACTIVE =
            findMethod(TRANSACTION_SYNCHRONIZATION_MANAGER, "isActualTransactionActive");

    /**
     * Obtains a connection through Spring's {@code DataSourceUtils} when available, otherwise directly
     * from the {@link DataSource}.
     */
    public static Connection getConnection(DataSource dataSource) throws SQLException {
        if (GET_CONNECTION == null) {
            return dataSource.getConnection();
        }
        return invokeConnection(GET_CONNECTION, dataSource);
    }

    /**
     * Releases a connection through Spring's {@code DataSourceUtils} when available, otherwise closes it.
     */
    public static void releaseConnection(Connection connection, DataSource dataSource) throws SQLException {
        if (connection == null) {
            return;
        }
        if (RELEASE_CONNECTION == null) {
            if (!connection.isClosed()) {
                connection.close();
            }
            return;
        }
        invokeVoid(RELEASE_CONNECTION, connection, dataSource);
    }

    /**
     * Returns {@code true} when the connection is wrapped by Spring's
     * {@code TransactionAwareDataSourceProxy} (commit/rollback owned by Spring).
     */
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

    /**
     * Returns {@code true} when Spring has bound this connection to the current transaction
     * for the given {@link DataSource}.
     */
    public static boolean isConnectionTransactional(Connection connection, DataSource dataSource) {
        if (connection == null || dataSource == null || IS_CONNECTION_TRANSACTIONAL == null) {
            return false;
        }
        return invokeBoolean(IS_CONNECTION_TRANSACTIONAL, connection, dataSource);
    }

    /**
     * Returns {@code true} when Spring reports an active transaction on the current thread.
     */
    public static boolean isActualTransactionActive() {
        if (IS_ACTUAL_TRANSACTION_ACTIVE == null) {
            return false;
        }
        return invokeBoolean(IS_ACTUAL_TRANSACTION_ACTIVE);
    }

    private static Method findMethod(String className, String methodName, Class<?>... parameterTypes) {
        try {
            return Class.forName(className).getMethod(methodName, parameterTypes);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Connection invokeConnection(Method method, Object... args) throws SQLException {
        try {
            return (Connection) method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new SQLException("Cannot access Spring JDBC method " + method.getName(), e);
        } catch (InvocationTargetException e) {
            throw asSqlException(method, e.getTargetException());
        }
    }

    private static void invokeVoid(Method method, Object... args) throws SQLException {
        try {
            method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new SQLException("Cannot access Spring JDBC method " + method.getName(), e);
        } catch (InvocationTargetException e) {
            throw asSqlException(method, e.getTargetException());
        }
    }

    private static boolean invokeBoolean(Method method, Object... args) {
        try {
            return Boolean.TRUE.equals(method.invoke(null, args));
        } catch (IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }

    private static SQLException asSqlException(Method method, Throwable cause) {
        if (cause instanceof SQLException sqlException) {
            return sqlException;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new SQLException("Spring JDBC method failed: " + method.getName(), cause);
    }
}
