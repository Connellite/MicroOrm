package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.JdbcValueMapper;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database-specific identifier quoting, DDL, DML SQL generation, and JDBC value mapping.
 * Obtain a singleton from {@link io.github.connellite.microorm.MicroOrm} factory methods
 * ({@code SqliteDialect.INSTANCE}, etc.) or pass to {@link io.github.connellite.microorm.MicroOrm#MicroOrm}.
 */
public interface Dialect {

    /** Renders an identifier for SQL (DDL/DML), applying dialect quoting and default case rules. */
    String sqlName(SqlIdentifier identifier);

    /** Name as stored in the database catalog (metadata queries, schema sync). */
    String catalogName(SqlIdentifier identifier);

    /**
     * Column label for {@link java.sql.ResultSet#getObject(String)} when hydrating entities.
     * Defaults to {@link #catalogName(SqlIdentifier)}.
     */
    default String jdbcColumnLabel(SqlIdentifier identifier) {
        return catalogName(identifier);
    }

    /** Dialect-specific {@link SqlGenerator} for entity CRUD statements. */
    SqlGenerator sqlGenerator();

    /** Converts Java field values to JDBC parameters and back (UUID storage, booleans, etc.). */
    JdbcValueMapper valueMapper();

    /** Creates the entity table and indexes when missing. */
    void createTable(Connection c, EntityModel model) throws SQLException;

    /** Adds missing nullable columns and indexes without dropping data. */
    void syncTable(Connection c, EntityModel model) throws SQLException;

    /** Drops the entity table (destructive). */
    void dropTable(Connection c, EntityModel model) throws SQLException;
}
