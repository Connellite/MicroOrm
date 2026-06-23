package io.github.connellite.stoneorm.dialect;

import io.github.connellite.stoneorm.mapping.EntityModel;

import java.sql.Connection;
import java.sql.SQLException;

public interface Dialect {

    String quote(String identifier);

    void createTable(Connection c, EntityModel model) throws SQLException;

    void dropTable(Connection c, EntityModel model) throws SQLException;
}
