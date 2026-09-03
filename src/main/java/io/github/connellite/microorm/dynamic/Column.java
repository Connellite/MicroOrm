package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.generation.IdGeneration;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runtime column descriptor for a {@link DynamicTable}. Values are accessed by {@link #name()} in CRUD maps.
 */
public final class Column {

    private final SqlIdentifier columnIdentifier;
    private final LogicalType type;
    private final String sqlTypeOverride;
    private final boolean primaryKey;
    private final IdGeneration idGeneration;
    private final boolean nullable;
    private final boolean unique;
    private final boolean indexed;
    private final int length;

    private Column(
            SqlIdentifier columnIdentifier,
            LogicalType type,
            String sqlTypeOverride,
            boolean primaryKey,
            IdGeneration idGeneration,
            boolean nullable,
            boolean unique,
            boolean indexed,
            int length) {
        this.columnIdentifier = columnIdentifier;
        this.type = Objects.requireNonNull(type, "type");
        this.sqlTypeOverride = sqlTypeOverride == null ? "" : sqlTypeOverride;
        this.primaryKey = primaryKey;
        this.idGeneration = idGeneration == null ? IdGeneration.none() : idGeneration;
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
        return idGeneration.identity();
    }

    /** Primary key generation metadata. */
    public IdGeneration idGeneration() {
        return idGeneration;
    }

    /** {@code true} when the primary key is allocated from a database sequence before insert. */
    public boolean sequenceGenerated() {
        return idGeneration.sequence();
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
        private GenerationType generatedStrategy;
        private String generatedGenerator = "";
        private final Map<String, SequenceGeneratorSpec> sequenceGenerators = new HashMap<>();
        private final Map<String, GenericGeneratorSpec> genericGenerators = new HashMap<>();
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

        /** Enables identity/autoincrement primary key generation. */
        public Builder generatedValue() {
            return generatedValue(GenerationType.IDENTITY);
        }

        /** Enables primary key generation with the given strategy. */
        public Builder generatedValue(GenerationType strategy) {
            return generatedValue(strategy, "");
        }

        /** Enables primary key generation with the given strategy and named generator. */
        public Builder generatedValue(GenerationType strategy, String generator) {
            this.generatedStrategy = Objects.requireNonNull(strategy, "strategy");
            this.generatedGenerator = generator == null ? "" : generator.trim();
            return this;
        }

        /** Enables primary key generation with a named generator. */
        public Builder generatedValue(String generator) {
            return generatedValue(GenerationType.IDENTITY, generator);
        }

        /** Declares a named sequence generator for this column. */
        public Builder sequenceGenerator(String name, Consumer<SequenceGeneratorBuilder> config) {
            String generatorName = requireGeneratorName(name);
            if (genericGenerators.containsKey(generatorName)) {
                throw new IllegalArgumentException("Generator already declared as generic: " + generatorName);
            }
            SequenceGeneratorBuilder builder = new SequenceGeneratorBuilder(generatorName);
            if (config != null) {
                config.accept(builder);
            }
            sequenceGenerators.put(generatorName, builder.build());
            return this;
        }

        /** Declares a named generic generator. Supported strategy: {@code native}. */
        public Builder genericGenerator(String name, String strategy) {
            String generatorName = requireGeneratorName(name);
            if (sequenceGenerators.containsKey(generatorName)) {
                throw new IllegalArgumentException("Generator already declared as sequence: " + generatorName);
            }
            genericGenerators.put(generatorName, new GenericGeneratorSpec(
                    generatorName,
                    Objects.requireNonNull(strategy, "strategy").trim()));
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
            IdGeneration idGeneration = resolveIdGeneration();
            if (idGeneration.generated() && !primaryKey) {
                throw new IllegalArgumentException("generatedValue requires primaryKey on column: " + name);
            }
            if (idGeneration.generated() && type != LogicalType.INT && type != LogicalType.LONG) {
                throw new IllegalArgumentException("generatedValue supports INT or LONG only: " + name);
            }
            return new Column(
                    SqlIdentifier.unquoted(name),
                    type,
                    sqlTypeOverride,
                    primaryKey,
                    idGeneration,
                    primaryKey || nullable,
                    unique,
                    indexed,
                    length);
        }

        private IdGeneration resolveIdGeneration() {
            if (generatedStrategy == null) {
                return IdGeneration.none();
            }
            if (generatedGenerator.isEmpty()) {
                return generatedStrategy == GenerationType.SEQUENCE
                        ? IdGeneration.sequence("", "", IdGeneration.DEFAULT_ALLOCATION_SIZE, IdGeneration.DEFAULT_INITIAL_VALUE)
                        : IdGeneration.identity("");
            }
            GenericGeneratorSpec genericGenerator = genericGenerators.get(generatedGenerator);
            SequenceGeneratorSpec sequenceGenerator = sequenceGenerators.get(generatedGenerator);
            if (genericGenerator != null && sequenceGenerator != null) {
                throw new IllegalArgumentException("Generator name is declared more than once: " + generatedGenerator);
            }
            if (genericGenerator != null) {
                if (!"native".equalsIgnoreCase(genericGenerator.strategy())) {
                    throw new IllegalArgumentException("Unsupported generic generator strategy: " + genericGenerator.strategy());
                }
                if (generatedStrategy == GenerationType.SEQUENCE) {
                    throw new IllegalArgumentException("genericGenerator(\"native\") cannot be used with SEQUENCE");
                }
                return IdGeneration.identity(generatedGenerator);
            }
            if (sequenceGenerator != null) {
                if (generatedStrategy != GenerationType.SEQUENCE) {
                    throw new IllegalArgumentException("sequenceGenerator requires GenerationType.SEQUENCE");
                }
                return IdGeneration.sequence(
                        generatedGenerator,
                        sequenceGenerator.sequenceName(),
                        sequenceGenerator.allocationSize(),
                        sequenceGenerator.initialValue());
            }
            throw new IllegalArgumentException("Unknown generated value generator: " + generatedGenerator);
        }

        private static String requireGeneratorName(String name) {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Generator name cannot be blank");
            }
            return name.trim();
        }
    }

    /** Fluent builder for dynamic sequence generator options. */
    public static final class SequenceGeneratorBuilder {

        private final String name;
        private String sequenceName = "";
        private int allocationSize = IdGeneration.DEFAULT_ALLOCATION_SIZE;
        private int initialValue = IdGeneration.DEFAULT_INITIAL_VALUE;

        private SequenceGeneratorBuilder(String name) {
            this.name = name;
        }

        public SequenceGeneratorBuilder sequenceName(String sequenceName) {
            this.sequenceName = sequenceName == null ? "" : sequenceName.trim();
            return this;
        }

        public SequenceGeneratorBuilder allocationSize(int allocationSize) {
            this.allocationSize = allocationSize;
            return this;
        }

        public SequenceGeneratorBuilder initialValue(int initialValue) {
            this.initialValue = initialValue;
            return this;
        }

        private SequenceGeneratorSpec build() {
            if (allocationSize <= 0) {
                throw new IllegalArgumentException("allocationSize must be positive for generator: " + name);
            }
            if (initialValue <= 0) {
                throw new IllegalArgumentException("initialValue must be positive for generator: " + name);
            }
            return new SequenceGeneratorSpec(name, sequenceName, allocationSize, initialValue);
        }
    }

    private record SequenceGeneratorSpec(String name, String sequenceName, int allocationSize, int initialValue) {
    }

    private record GenericGeneratorSpec(String name, String strategy) {
    }
}
