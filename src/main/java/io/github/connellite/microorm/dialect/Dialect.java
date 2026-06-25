package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.JdbcValueMapper;

import java.sql.Connection;
import java.sql.SQLException;

/** Database-specific quoting, DDL, SQL generation, and JDBC value mapping. */
public interface Dialect {

    /** Renders an identifier for SQL (DDL/DML). */
    String sqlName(SqlIdentifier identifier);

    /** Name as stored in the database catalog (metadata queries, ResultSet labels). */
    String catalogName(SqlIdentifier identifier);

    /** Column label for {@link java.sql.ResultSet#getObject(String)}. */
    default String jdbcColumnLabel(SqlIdentifier identifier) {
        return catalogName(identifier);
    }

    SqlGenerator sqlGenerator();

    JdbcValueMapper valueMapper();

    void createTable(Connection c, EntityModel model) throws SQLException;

    void syncTable(Connection c, EntityModel model) throws SQLException;

    void dropTable(Connection c, EntityModel model) throws SQLException;
}
