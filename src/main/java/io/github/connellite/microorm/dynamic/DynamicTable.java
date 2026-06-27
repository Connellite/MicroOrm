package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable runtime table definition: physical table name, columns, and primary key.
 * Register instances in {@link DynamicTableRegistry} before use with {@link DynamicSession}.
 */
public final class DynamicTable {

    private final String name;
    private final SqlIdentifier tableIdentifier;
    private final List<Column> columns;
    private final Column primaryKey;

    private DynamicTable(String name, SqlIdentifier tableIdentifier, List<Column> columns, Column primaryKey) {
        this.name = name;
        this.tableIdentifier = tableIdentifier;
        this.columns = List.copyOf(columns);
        this.primaryKey = primaryKey;
    }

    /** Registry key used with {@link DynamicSession} methods. */
    public String name() {
        return name;
    }

    /** Physical table identifier for SQL and DDL. */
    public SqlIdentifier tableIdentifier() {
        return tableIdentifier;
    }

    /** Logical table name text (without SQL quoting). */
    public String tableName() {
        return tableIdentifier.text();
    }

    /** All columns in declaration order. */
    public List<Column> columns() {
        return columns;
    }

    /** Primary key column. */
    public Column primaryKey() {
        return primaryKey;
    }

    /** Returns a column by logical name or throws {@link MicroOrmException}. */
    public Column columnByName(String columnName) {
        Objects.requireNonNull(columnName, "columnName");
        for (Column column : columns) {
            if (column.name().equals(columnName)) {
                return column;
            }
        }
        throw new MicroOrmException("Unknown column '" + columnName + "' on dynamic table '" + name + "'");
    }

    /** Starts building a runtime table definition. */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Fluent builder for {@link DynamicTable}. */
    public static final class Builder {

        private final String name;
        private String tableName;
        private final List<Column> columns = new ArrayList<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Table name cannot be blank");
            }
        }

        /** Sets the physical table name; defaults to {@link #name} when omitted. */
        public Builder table(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /** Adds a pre-built column. */
        public Builder column(Column column) {
            columns.add(Objects.requireNonNull(column, "column"));
            return this;
        }

        /** Adds a column via {@link Column#builder(String, LogicalType)} without extra configuration. */
        public Builder column(String columnName, LogicalType type) {
            return column(columnName, type, null);
        }

        /** Adds a column via {@link Column#builder(String, LogicalType)}. */
        public Builder column(String columnName, LogicalType type, java.util.function.Consumer<Column.Builder> config) {
            Column.Builder builder = Column.builder(columnName, type);
            if (config != null) {
                config.accept(builder);
            }
            return column(builder.build());
        }

        /** Builds an immutable table definition. */
        public DynamicTable build() {
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("Dynamic table requires at least one column: " + name);
            }
            String physical = tableName == null || tableName.isBlank() ? name : tableName;
            SqlGenerator.validateIdentifier(physical, "table");
            Column pk = null;
            for (Column column : columns) {
                if (column.primaryKey()) {
                    if (pk != null) {
                        throw new IllegalArgumentException("Multiple primary keys on dynamic table: " + name);
                    }
                    pk = column;
                }
            }
            if (pk == null) {
                throw new IllegalArgumentException("Dynamic table requires a primary key column: " + name);
            }
            return new DynamicTable(name, SqlIdentifier.unquoted(physical), columns, pk);
        }
    }
}
