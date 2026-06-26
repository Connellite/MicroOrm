package io.github.connellite.microorm.exception;

import java.sql.SQLException;

/** Unchecked wrapper for ORM failures. */
public final class MicroOrmException extends RuntimeException {

    public MicroOrmException(String message) {
        super(message);
    }

    public MicroOrmException(String message, Throwable cause) {
        super(message, cause);
    }

    public static MicroOrmException wrap(SQLException e) {
        return new MicroOrmException(e.getMessage(), e);
    }
}
