package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.annotation.UuidGenerator;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.generation.IdGeneration;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.AttributeConverter;
import io.github.connellite.reflection.ReflectionUtil;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
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
    private final AttributeConverter<Object, Object> converter;
    private final Class<?> converterAttributeType;
    private final Class<?> converterDatabaseType;

    private Column(
            SqlIdentifier columnIdentifier,
            LogicalType type,
            String sqlTypeOverride,
            boolean primaryKey,
            IdGeneration idGeneration,
            boolean nullable,
            boolean unique,
            boolean indexed,
            int length,
            AttributeConverter<?, ?> converter,
            Class<?> converterAttributeType,
            Class<?> converterDatabaseType) {
        this.columnIdentifier = columnIdentifier;
        this.type = Objects.requireNonNull(type, "type");
        this.sqlTypeOverride = sqlTypeOverride == null ? "" : sqlTypeOverride;
        this.primaryKey = primaryKey;
        this.idGeneration = idGeneration == null ? IdGeneration.none() : idGeneration;
        this.nullable = nullable;
        this.unique = unique;
        this.indexed = indexed;
        this.length = length;
        @SuppressWarnings("unchecked")
        AttributeConverter<Object, Object> typedConverter = (AttributeConverter<Object, Object>) converter;
        this.converter = typedConverter;
        this.converterAttributeType = converterAttributeType;
        this.converterDatabaseType = converterDatabaseType;
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

    /** {@code true} when the primary key is generated as a UUID before insert. */
    public boolean uuidGenerated() {
        return idGeneration.uuid();
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

    /** Preferred Java value type for Map-based CRUD before converter application. */
    public Class<?> javaType() {
        return converterAttributeType == null ? type.javaType() : converterAttributeType;
    }

    /** Java type stored in JDBC after applying the dynamic converter, when present. */
    public Class<?> jdbcJavaType() {
        return converterDatabaseType == null ? type.javaType() : converterDatabaseType;
    }

    /** Returns whether this column has an attribute converter. */
    public boolean converted() {
        return converter != null;
    }

    /** Converter map-side value type, or {@code null} when not converted. */
    public Class<?> converterAttributeType() {
        return converterAttributeType;
    }

    /** Converter database-side Java type, or {@code null} when not converted. */
    public Class<?> converterDatabaseType() {
        return converterDatabaseType;
    }

    /** Converts a Map value to its database-side Java value. */
    public Object convertToDatabaseColumn(Object value) {
        return converter == null ? value : converter.convertToDatabaseColumn(value);
    }

    /** Converts a database-side Java value to its Map value. */
    public Object convertToEntityAttribute(Object value) {
        return converter == null ? value : converter.convertToEntityAttribute(value);
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
        private UuidGenerator.Version uuidGeneratorVersion;
        private final Map<String, SequenceGeneratorSpec> sequenceGenerators = new HashMap<>();
        private final Map<String, GenericGeneratorSpec> genericGenerators = new HashMap<>();
        private boolean nullable = true;
        private boolean unique;
        private boolean indexed;
        private int length;
        private AttributeConverter<?, ?> converter;
        private Class<?> converterAttributeType;
        private Class<?> converterDatabaseType;

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

        /** Enables UUID primary key generation with the default UUID version. */
        public Builder uuidGenerator() {
            return uuidGenerator(UuidGenerator.Version.VERSION_4);
        }

        /** Enables UUID primary key generation with the selected UUID version. */
        public Builder uuidGenerator(UuidGenerator.Version version) {
            this.uuidGeneratorVersion = Objects.requireNonNull(version, "version");
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

        /** Applies a converter between Map values and the column's database-side logical type. */
        public Builder converter(Class<? extends AttributeConverter<?, ?>> converterClass) {
            Objects.requireNonNull(converterClass, "converterClass");
            AttributeConverter<?, ?> converter;
            try {
                converter = ReflectionUtil.getInstance(converterClass);
            } catch (ReflectiveOperationException e) {
                throw new MicroOrmException("Cannot instantiate converter " + converterClass.getName()
                        + " for dynamic column '" + name + "'", e);
            }
            List<Class<?>> types = converterTypes(converterClass);
            if (types.size() < 2) {
                throw new MicroOrmException("Converter " + converterClass.getName()
                        + " must declare AttributeConverter<Attribute, Database>");
            }
            this.converter = converter;
            this.converterAttributeType = types.get(0);
            this.converterDatabaseType = types.get(1);
            return this;
        }

        /** Builds an immutable column descriptor. */
        public Column build() {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Column name cannot be blank");
            }
            SqlGenerator.validateIdentifier(name, "column");
            validateConverterDatabaseType(name, type, converterDatabaseType);
            IdGeneration idGeneration = resolveIdGeneration();
            if (idGeneration.generated() && !primaryKey) {
                throw new IllegalArgumentException("Generated primary key generation requires primaryKey on column: " + name);
            }
            if (idGeneration.uuid() && type != LogicalType.UUID) {
                throw new IllegalArgumentException("uuidGenerator supports UUID only: " + name);
            }
            if (idGeneration.generated() && !idGeneration.uuid() && type != LogicalType.INT && type != LogicalType.LONG) {
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
                    length,
                    converter,
                    converterAttributeType,
                    converterDatabaseType);
        }

        private IdGeneration resolveIdGeneration() {
            if (uuidGeneratorVersion != null) {
                if (generatedStrategy != null) {
                    throw new IllegalArgumentException("uuidGenerator cannot be combined with generatedValue");
                }
                return IdGeneration.uuid(uuidGeneratorVersion.number());
            }
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
                return IdGeneration.sequence(generatedGenerator, sequenceGenerator.sequenceName(), sequenceGenerator.allocationSize(), sequenceGenerator.initialValue());
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

        private static void validateConverterDatabaseType(String columnName, LogicalType type, Class<?> converterDatabaseType) {
            if (converterDatabaseType == null) {
                return;
            }
            Class<?> columnType = ReflectionUtil.primitiveToWrapper(type.javaType());
            Class<?> databaseType = ReflectionUtil.primitiveToWrapper(converterDatabaseType);
            if (!columnType.isAssignableFrom(databaseType) && !databaseType.isAssignableFrom(columnType)) {
                throw new MicroOrmException("Converter database type " + converterDatabaseType.getName()
                        + " does not match dynamic column '" + columnName + "' logical type " + type);
            }
        }

        private static List<Class<?>> converterTypes(Class<?> converterClass) {
            for (Type genericInterface : converterClass.getGenericInterfaces()) {
                List<Class<?>> classes = converterTypes(genericInterface);
                if (!classes.isEmpty()) {
                    return classes;
                }
            }
            Class<?> superClass = converterClass.getSuperclass();
            return superClass == null || superClass == Object.class ? List.of() : converterTypes(superClass);
        }

        private static List<Class<?>> converterTypes(Type type) {
            if (type instanceof ParameterizedType parameterized
                    && parameterized.getRawType() == AttributeConverter.class) {
                return ReflectionUtil.getAllGenericParameterClasses(parameterized);
            }
            if (type instanceof Class<?> clazz) {
                return converterTypes(clazz);
            }
            if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> rawClass) {
                return converterTypes(rawClass);
            }
            return List.of();
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
