package io.github.connellite.microorm.mapping;

/**
 * Metadata describing how a primary key value is generated.
 */
public record IdGeneration(IdGenerationKind kind, String generatorName, String sequenceName, int allocationSize,
                           int initialValue) {

    public static final int DEFAULT_ALLOCATION_SIZE = 1;
    public static final int DEFAULT_INITIAL_VALUE = 1;

    public IdGeneration {
        kind = kind == null ? IdGenerationKind.NONE : kind;
        generatorName = generatorName == null ? "" : generatorName;
        sequenceName = sequenceName == null ? "" : sequenceName;
        if (allocationSize <= 0) {
            throw new IllegalArgumentException("allocationSize must be positive");
        }
        if (initialValue <= 0) {
            throw new IllegalArgumentException("initialValue must be positive");
        }
    }

    public static IdGeneration none() {
        return new IdGeneration(
                IdGenerationKind.NONE,
                "",
                "",
                DEFAULT_ALLOCATION_SIZE,
                DEFAULT_INITIAL_VALUE);
    }

    public static IdGeneration identity(String generatorName) {
        return new IdGeneration(
                IdGenerationKind.IDENTITY,
                generatorName,
                "",
                DEFAULT_ALLOCATION_SIZE,
                DEFAULT_INITIAL_VALUE);
    }

    public static IdGeneration sequence(String generatorName, String sequenceName, int allocationSize, int initialValue) {
        return new IdGeneration(
                IdGenerationKind.SEQUENCE,
                generatorName,
                sequenceName,
                allocationSize,
                initialValue);
    }

    public boolean generated() {
        return kind != IdGenerationKind.NONE;
    }

    public boolean identity() {
        return kind == IdGenerationKind.IDENTITY;
    }

    public boolean sequence() {
        return kind == IdGenerationKind.SEQUENCE;
    }
}
