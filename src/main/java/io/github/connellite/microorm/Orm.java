package io.github.connellite.microorm;

import io.github.connellite.microorm.connection.ConnectionProvider;
import io.github.connellite.microorm.connection.DataSourceConnectionProvider;
import io.github.connellite.microorm.connection.KeepOpenConnectionProvider;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.session.Session;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Entry point for MicroOrm. Choose a dialect factory, {@link #register(Class[]) register} entity classes,
 * then {@link #openSession()} for CRUD and schema operations.
 */
public final class Orm {

    private final Dialect dialect;
    private final ConnectionProvider provider;
    private final EntityModelRegistry registry;

    public Orm(Dialect dialect, ConnectionProvider provider, EntityModelRegistry registry) {
        this.dialect = dialect;
        this.provider = provider;
        this.registry = registry;
    }

    /** SQLite with a single JDBC connection (typical tests); connection is not closed by {@link Session#close()}. */
    public static Orm sqlite(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new Orm(SqliteDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    /** SQLite backed by a {@link DataSource} (pool-friendly; connection released on {@link Session#close()}). */
    public static Orm sqlite(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new Orm(SqliteDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    public static Orm postgres(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new Orm(PostgresDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    public static Orm postgres(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new Orm(PostgresDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    public static Orm mysql(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new Orm(MysqlDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    public static Orm mysql(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new Orm(MysqlDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    public static Orm mssql(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new Orm(MssqlDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    public static Orm mssql(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new Orm(MssqlDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    public static Orm oracle(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new Orm(OracleDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    public static Orm oracle(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new Orm(OracleDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    /** Registers entity classes and returns {@code this} for chaining. */
    public Orm register(Class<?>... entityClasses) {
        Objects.requireNonNull(entityClasses, "entityClasses");
        for (Class<?> c : entityClasses) {
            Objects.requireNonNull(c, "entity class cannot be null");
            registry.register(c);
        }
        return this;
    }

    /** Opens a new session with a connection acquired from the configured provider. */
    public Session openSession() throws SQLException {
        Connection c = provider.acquire();
        return new Session(c, provider, registry, dialect);
    }

    /**
     * Opens a session, runs {@code action}, and closes the session (including on failure).
     * Prefer this over manual {@link #openSession()} when using a pooled {@link DataSource}.
     */
    public <T> T withSession(SessionAction<T> action) throws SQLException {
        Objects.requireNonNull(action, "action");
        try (Session session = openSession()) {
            return action.apply(session);
        }
    }

    /** Callback for {@link #withSession(SessionAction)}. */
    @FunctionalInterface
    public interface SessionAction<T> {
        T apply(Session session) throws SQLException;
    }

    public EntityModelRegistry registry() {
        return registry;
    }
}
