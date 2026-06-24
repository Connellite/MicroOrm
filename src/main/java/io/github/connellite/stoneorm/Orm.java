package io.github.connellite.stoneorm;

import io.github.connellite.stoneorm.connection.ConnectionProvider;
import io.github.connellite.stoneorm.connection.DataSourceConnectionProvider;
import io.github.connellite.stoneorm.connection.KeepOpenConnectionProvider;
import io.github.connellite.stoneorm.dialect.Dialect;
import io.github.connellite.stoneorm.dialect.MssqlDialect;
import io.github.connellite.stoneorm.dialect.MysqlDialect;
import io.github.connellite.stoneorm.dialect.OracleDialect;
import io.github.connellite.stoneorm.dialect.PostgresDialect;
import io.github.connellite.stoneorm.dialect.SqliteDialect;
import io.github.connellite.stoneorm.mapping.EntityModelRegistry;
import io.github.connellite.stoneorm.session.Session;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

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
        return new Orm(SqliteDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    public static Orm sqlite(DataSource dataSource) {
        return new Orm(SqliteDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    public static Orm postgres(Connection connection) {
        return new Orm(PostgresDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    public static Orm postgres(DataSource dataSource) {
        return new Orm(PostgresDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    public static Orm mysql(Connection connection) {
        return new Orm(MysqlDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    public static Orm mysql(DataSource dataSource) {
        return new Orm(MysqlDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    public static Orm mssql(Connection connection) {
        return new Orm(MssqlDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    public static Orm mssql(DataSource dataSource) {
        return new Orm(MssqlDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    public static Orm oracle(Connection connection) {
        return new Orm(OracleDialect.INSTANCE, new KeepOpenConnectionProvider(connection), new EntityModelRegistry());
    }

    public static Orm oracle(DataSource dataSource) {
        return new Orm(OracleDialect.INSTANCE, new DataSourceConnectionProvider(dataSource), new EntityModelRegistry());
    }

    public Orm register(Class<?>... entityClasses) {
        for (Class<?> c : entityClasses) {
            registry.register(c);
        }
        return this;
    }

    public Session openSession() throws SQLException {
        Connection c = provider.acquire();
        return new Session(c, provider, registry, dialect);
    }

    public EntityModelRegistry registry() {
        return registry;
    }
}
