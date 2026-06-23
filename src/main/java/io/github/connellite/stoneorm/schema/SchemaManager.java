package io.github.connellite.stoneorm.schema;

import io.github.connellite.stoneorm.mapping.EntityModel;

import java.sql.Connection;
import java.sql.SQLException;

public interface SchemaManager {

    void createTable(Connection connection, EntityModel model) throws SQLException;

    void syncTable(Connection connection, EntityModel model) throws SQLException;

    void dropTable(Connection connection, EntityModel model) throws SQLException;
}
