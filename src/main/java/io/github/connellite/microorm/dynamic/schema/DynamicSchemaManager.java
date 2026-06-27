package io.github.connellite.microorm.dynamic.schema;

import io.github.connellite.microorm.dynamic.Column;
import io.github.connellite.microorm.dynamic.DynamicTable;
import io.github.connellite.microorm.dynamic.LogicalType;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DDL operations for runtime-defined {@link DynamicTable} instances.
 */
public interface DynamicSchemaManager {

    /** Creates the table when absent; never drops or truncates existing data. */
    void createTable(Connection connection, DynamicTable table) throws SQLException;

    /** Adds missing nullable columns and indexes; never drops tables or truncates data. */
    void syncTable(Connection connection, DynamicTable table) throws SQLException;

    /** Drops the table when present (destructive). */
    void dropTable(Connection connection, DynamicTable table) throws SQLException;

    /** {@code true} when the physical table already exists. */
    boolean tableExists(Connection connection, DynamicTable table) throws SQLException;
}
