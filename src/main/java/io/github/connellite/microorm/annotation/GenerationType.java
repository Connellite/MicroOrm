package io.github.connellite.microorm.annotation;

/** Primary key generation strategies supported by MicroOrm. */
public enum GenerationType {
    /** Database identity/autoincrement column; the key is read from JDBC generated keys after insert. */
    IDENTITY,

    /** Database sequence; the key is allocated before insert and included in the inserted row. */
    SEQUENCE
}
