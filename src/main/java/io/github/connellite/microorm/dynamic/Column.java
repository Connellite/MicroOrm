package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.util.Objects;

/**
 * Runtime column descriptor for a {@link DynamicTable}. Values are accessed by {@link #name()} in CRUD maps.
 */
public final class Column {

    private final SqlIdentifier columnIdentifier;
    private final LogicalType type;
    private final String sqlTypeOverride;
    private final boolean primaryKey;
    private final boolean autoIncrement;
    private final boolean nullable;
    private final boolean unique;
    private final boolean indexed;
    private final int length;

    private Column(
            SqlIdentifier columnIdentifier,
            LogicalType type,
            String sqlTypeOverride,
            boolean primaryKey,
            boolean autoIncrement,
            boolean nullable,
            boolean unique,
            boolean indexed,
            int length) {
        this.columnIdentifier = columnIdentifier;
        this.type = Objects.requireNonNull(type, "type");
        this.sqlTypeOverride = sqlTypeOverride == null ? "" : sqlTypeOverride;
        this.primaryKey = primaryKey;
        this.autoIncrement = autoIncrement;
        this.nullable = nullable;
        this.unique = unique;
        this.indexed = indexed;
        this.length = length;
    }

    /** Logical column name used as the key in insert/update/select maps. */
    public String name() {
        return columnIdentifier.text();
    }

    /** Physical column identifier for SQL rendering. */
    public SqlIdentifier columnIdentifier() {
        return columnIdentifier;
    }

    /** Logical type of the column. */
    public LogicalType type() {
        return type;
    }

    /** Optional explicit SQL type ({@code NVARCHAR(255)}, {@code NUMBER(10)}, etc.). */
    public String sqlTypeOverride() {
        return sqlTypeOverride;
    }

    /** {@code true} when this column is the table primary key. */
    public boolean primaryKey() {
        return primaryKey;
    }

    /** {@code true} when the primary key is database-generated on insert. */
    public boolean autoIncrement() {
        return autoIncrement;
    }

    /** {@code true} when SQL NULL is allowed. Primary keys are never nullable. */
    public boolean nullable() {
        return nullable;
    }

    /** {@code true} when a UNIQUE constraint is declared on create. */
    public boolean unique() {
        return unique;
    }

    /** {@code true} when a secondary index should be created. */
    public boolean indexed() {
        return indexed;
    }

    /** Optional length hint for string types in DDL. */
    public int length() {
        return length;
    }

    /** Starts building a column with the given logical name and type. */
    public static Builder builder(String name, LogicalType type) {
        return new Builder(name, type);
    }

    /** Fluent builder for {@link Column}. */
    public static final class Builder {

        private final String name;
        private final LogicalType type;
        private String sqlTypeOverride = "";
        private boolean primaryKey;
        private boolean autoIncrement;
        private boolean nullable = true;
        private boolean unique;
        private boolean indexed;
        private int length;

        private Builder(String name, LogicalType type) {
            this.name = name;
            this.type = Objects.requireNonNull(type, "type");
        }

        /** Marks this column as the primary key. */
        public Builder primaryKey() {
            this.primaryKey = true;
            this.nullable = false;
            return this;
        }

        /** Enables database-generated values for a numeric primary key. */
        public Builder autoIncrement() {
            this.autoIncrement = true;
            return this;
        }

        /** Requires a non-null value. Ignored for primary keys (always NOT NULL). */
        public Builder notNull() {
            this.nullable = false;
            return this;
        }

        /** Allows SQL NULL (default for non-primary-key columns). */
        public Builder nullable() {
            this.nullable = true;
            return this;
        }

        /** Adds a UNIQUE constraint when the table is created. */
        public Builder unique() {
            this.unique = true;
            return this;
        }

        /** Creates a secondary index on this column. */
        public Builder indexed() {
            this.indexed = true;
            return this;
        }

        /** Sets an explicit SQL type instead of the dialect default for {@link #type}. */
        public Builder sqlType(String sqlType) {
            this.sqlTypeOverride = sqlType;
            return this;
        }

        /** Sets a length hint for string DDL ({@code VARCHAR(n)}). */
        public Builder length(int length) {
            this.length = length;
            return this;
        }

        /** Builds an immutable column descriptor. */
        public Column build() {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Column name cannot be blank");
            }
            SqlGenerator.validateIdentifier(name, "column");
            if (autoIncrement && !primaryKey) {
                throw new IllegalArgumentException("autoIncrement requires primaryKey on column: " + name);
            }
            if (autoIncrement && type != LogicalType.INT && type != LogicalType.LONG) {
                throw new IllegalArgumentException("autoIncrement supports INT or LONG only: " + name);
            }
            return new Column(
                    SqlIdentifier.unquoted(name),
                    type,
                    sqlTypeOverride,
                    primaryKey,
                    autoIncrement,
                    primaryKey || nullable,
                    unique,
                    indexed,
                    length);
        }
    }
}
