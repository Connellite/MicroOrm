package io.github.connellite.microorm;

import org.testcontainers.containers.GenericContainer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

final class LockedConnectionSupport {

    private LockedConnectionSupport() {
    }

    /**
     * One container per dialect. The lock is held until {@link Connection#close()} so parallel
     * IntelliJ/JUnit runs cannot DDL-deadlock on the shared database (MySQL metadata locks
     * otherwise wait up to {@code lock_wait_timeout}, default one year).
     */
    static Connection open(
            ReentrantLock lock,
            GenericContainer<?> container,
            String name,
            Duration startupTimeout,
            DialectTestSupport.ConnectionFactory connect) throws SQLException {
        TestcontainersSupport.assumeDockerAvailable();
        lock.lock();
        boolean holdLock = true;
        try {
            if (!container.isRunning()) {
                System.err.println("[microorm-tests] Starting " + name + " Testcontainers (timeout " + startupTimeout.toMinutes() + "m)...");
                container.start();
                System.err.println("[microorm-tests] " + name + " is ready");
            }
            Connection wrapped = unlockOnClose(lock, openWithRetry(name, startupTimeout, connect));
            holdLock = false;
            return wrapped;
        } finally {
            if (holdLock) {
                lock.unlock();
            }
        }
    }

    private static Connection openWithRetry(String name, Duration timeout, DialectTestSupport.ConnectionFactory connect) throws SQLException {
        long deadline = System.nanoTime() + timeout.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try {
                return connect.open();
            } catch (SQLException e) {
                last = e;
                sleepBeforeRetry(name, e);
            }
        }
        throw last == null ? new SQLException("Timed out opening " + name + " Testcontainers connection") : last;
    }

    private static void sleepBeforeRetry(String name, SQLException cause) throws SQLException {
        try {
            Thread.sleep(1_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SQLException interrupted = new SQLException("Interrupted opening " + name + " Testcontainers connection", e);
            interrupted.addSuppressed(cause);
            throw interrupted;
        }
    }

    private static Connection unlockOnClose(ReentrantLock lock, Connection delegate) {
        AtomicBoolean released = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    try {
                        if ("close".equals(methodName)) {
                            try {
                                return method.invoke(delegate, args);
                            } finally {
                                if (released.compareAndSet(false, true) && lock.isHeldByCurrentThread()) {
                                    lock.unlock();
                                }
                            }
                        }
                        if ("unwrap".equals(methodName) && args != null && args.length == 1) {
                            Class<?> iface = (Class<?>) args[0];
                            if (iface.isInstance(proxy)) {
                                return proxy;
                            }
                            return delegate.unwrap(iface);
                        }
                        if ("isWrapperFor".equals(methodName) && args != null && args.length == 1) {
                            Class<?> iface = (Class<?>) args[0];
                            return iface.isInstance(proxy) || delegate.isWrapperFor(iface);
                        }
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        if (cause instanceof Error error) {
                            throw error;
                        }
                        if (cause instanceof SQLException sql) {
                            throw sql;
                        }
                        throw new SQLException(cause);
                    }
                });
    }
}
