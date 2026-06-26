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

    /** String representation (SQLite, MySQL, etc.). */
    STRING
}
