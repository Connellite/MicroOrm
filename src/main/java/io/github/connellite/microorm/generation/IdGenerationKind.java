package io.github.connellite.microorm.generation;

/** Internal primary key generation mode. */
public enum IdGenerationKind {
    NONE,
    IDENTITY,
    SEQUENCE,
    UUID
}
