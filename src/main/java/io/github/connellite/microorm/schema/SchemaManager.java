package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.mapping.EntityModel;

import java.sql.Connection;
import java.sql.SQLException;

public interface SchemaManager {

    /** Creates the table when absent; no-op when it already exists (data is preserved). */
    void createTable(Connection connection, EntityModel model) throws SQLException;

    /** Adds missing nullable columns and indexes; never drops tables or truncates data. */
    void syncTable(Connection connection, EntityModel model) throws SQLException;

    void dropTable(Connection connection, EntityModel model) throws SQLException;
}
