package io.github.connellite.microorm.exception;

import java.sql.SQLException;

/**
 * Unchecked exception for mapping validation failures, lazy-load errors, and wrapped JDBC problems.
 * DDL methods on {@link io.github.connellite.microorm.session.Session} may still throw {@link SQLException} directly.
 */
public final class MicroOrmException extends RuntimeException {

    public MicroOrmException(String message) {
        super(message);
    }

    public MicroOrmException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Wraps a checked {@link SQLException} as {@link MicroOrmException}. */
    public static MicroOrmException wrap(SQLException e) {
        return new MicroOrmException(e.getMessage(), e);
    }
}
