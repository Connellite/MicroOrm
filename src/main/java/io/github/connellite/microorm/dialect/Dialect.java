package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.type.JdbcValueMapper;

import java.sql.Connection;
import java.sql.SQLException;

/** Database-specific quoting, DDL, SQL generation, and JDBC value mapping. */
public interface Dialect {

    String quote(String identifier);

    SqlGenerator sqlGenerator();

    JdbcValueMapper valueMapper();

    void createTable(Connection c, EntityModel model) throws SQLException;

    void syncTable(Connection c, EntityModel model) throws SQLException;

    void dropTable(Connection c, EntityModel model) throws SQLException;
}
