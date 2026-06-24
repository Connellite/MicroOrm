package io.github.connellite.stoneorm.dialect;

import io.github.connellite.stoneorm.mapping.EntityModel;
import io.github.connellite.stoneorm.sql.SqlGenerator;
import io.github.connellite.stoneorm.type.JdbcValueMapper;

import java.sql.Connection;
import java.sql.SQLException;

public interface Dialect {

    String quote(String identifier);

    SqlGenerator sqlGenerator();

    JdbcValueMapper valueMapper();

    void createTable(Connection c, EntityModel model) throws SQLException;

    void syncTable(Connection c, EntityModel model) throws SQLException;

    void dropTable(Connection c, EntityModel model) throws SQLException;
}
