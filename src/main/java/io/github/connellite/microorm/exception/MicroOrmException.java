package io.github.connellite.microorm.exception;

import java.sql.SQLException;

/**
 * Unchecked exception for mapping validation failures, lazy-load errors, and wrapped JDBC problems.
 * DDL methods on {@link io.github.connellite.microorm.session.Session} may still throw {@link SQLException} directly.
 */
public final class MicroOrmException extends RuntimeException {

    /**
     * @param message human-readable failure description
     */
    public MicroOrmException(String message) {
        super(message);
    }

    /**
     * @param message human-readable failure description
     * @param cause underlying failure
     */
    public MicroOrmException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Wraps a checked {@link SQLException} as {@link MicroOrmException}.
     *
     * @param e JDBC failure to wrap
     * @return unchecked exception with the original cause preserved
     */
    public static MicroOrmException wrap(SQLException e) {
        return new MicroOrmException(e.getMessage(), e);
    }
}
