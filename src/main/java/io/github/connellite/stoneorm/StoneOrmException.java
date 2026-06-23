package io.github.connellite.stoneorm;

import java.sql.SQLException;

/** Unchecked wrapper for ORM failures. */
public final class StoneOrmException extends RuntimeException {

    public StoneOrmException(String message) {
        super(message);
    }

    public StoneOrmException(String message, Throwable cause) {
        super(message, cause);
    }

    public static StoneOrmException wrap(SQLException e) {
        return new StoneOrmException(e.getMessage(), e);
    }
}
