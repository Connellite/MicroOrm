package io.github.connellite.microorm.type;

/**
 * How {@link java.util.UUID} primary keys and columns are stored in JDBC.
 * Chosen per dialect in {@link io.github.connellite.microorm.dialect.Dialect#valueMapper()}.
 */
public enum UuidStorage {

    /** Native UUID type (PostgreSQL). */
    NATIVE,

    /** 16-byte binary (Oracle RAW). */
    BINARY,

    /** 16-byte Microsoft GUID binary layout (first 4-2-2 UUID fields little-endian). */
    MICROSOFT_GUID,

    /** String representation (SQLite, MySQL, etc.). */
    STRING
}
