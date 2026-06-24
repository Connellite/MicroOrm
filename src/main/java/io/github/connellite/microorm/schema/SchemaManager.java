package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.mapping.EntityModel;

import java.sql.Connection;
import java.sql.SQLException;

public interface SchemaManager {

    void createTable(Connection connection, EntityModel model) throws SQLException;

    void syncTable(Connection connection, EntityModel model) throws SQLException;

    void dropTable(Connection connection, EntityModel model) throws SQLException;
}
