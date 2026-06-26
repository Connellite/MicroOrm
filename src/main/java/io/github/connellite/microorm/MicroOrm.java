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
import io.github.connellite.microorm.mapping.SpringPhysicalNamingStrategy;
import io.github.connellite.microorm.session.Session;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Entry point for MicroOrm. Choose a dialect factory, {@link #register(Class[]) register} entity classes,
 * then {@link #openSession()} for CRUD and schema operations.
 */
public final class MicroOrm {

    private final Dialect dialect;
    private final ConnectionProvider provider;
    private final EntityModelRegistry registry;

    /** Creates a registry with Spring Boot-style snake_case physical names. */
    public static EntityModelRegistry springNamingRegistry() {
        return new EntityModelRegistry(SpringPhysicalNamingStrategy.INSTANCE);
    }

    /**
     * Constructs an ORM instance with a custom dialect, connection lifecycle, and entity metadata registry
     * (for example {@link #springNamingRegistry()}).
     */
    public MicroOrm(Dialect dialect, ConnectionProvider provider, EntityModelRegistry registry) {
        this.dialect = dialect;
        this.provider = provider;
        this.registry = registry;
    }

    /** SQLite with a single JDBC connection (typical tests); connection is not closed by {@link Session#close()}. */
    public static MicroOrm sqlite(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new MicroOrm(SqliteDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    /** SQLite backed by a {@link DataSource} (pool-friendly; connection released on {@link Session#close()}). */
    public static MicroOrm sqlite(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new MicroOrm(SqliteDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    /** PostgreSQL with a single JDBC connection; connection is not closed by {@link Session#close()}. */
    public static MicroOrm postgres(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new MicroOrm(PostgresDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    /** PostgreSQL backed by a {@link DataSource} (pool-friendly; connection released on {@link Session#close()}). */
    public static MicroOrm postgres(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new MicroOrm(PostgresDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    /** MySQL with a single JDBC connection; connection is not closed by {@link Session#close()}. */
    public static MicroOrm mysql(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new MicroOrm(MysqlDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    /** MySQL backed by a {@link DataSource}. */
    public static MicroOrm mysql(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new MicroOrm(MysqlDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    /** Microsoft SQL Server with a single JDBC connection. */
    public static MicroOrm mssql(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new MicroOrm(MssqlDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    /** Microsoft SQL Server backed by a {@link DataSource}. */
    public static MicroOrm mssql(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new MicroOrm(MssqlDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    /** Oracle with a single JDBC connection. */
    public static MicroOrm oracle(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new MicroOrm(OracleDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    /** Oracle backed by a {@link DataSource}. */
    public static MicroOrm oracle(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new MicroOrm(OracleDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    /** Registers entity classes and returns {@code this} for chaining. */
    public MicroOrm register(Class<?>... entityClasses) {
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

    /** Shared entity metadata registry (same instance passed to every {@link Session}). */
    public EntityModelRegistry registry() {
        return registry;
    }
}
