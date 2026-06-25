package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.MicroOrmException;

import java.util.Objects;

/**
 * SQL identifier with optional quoting (Hibernate/Spring JPA backtick convention).
 * {@code `size`} preserves case in quoted SQL; {@code size} uses the dialect default case.
 */
public record SqlIdentifier(String text, boolean quoted) {

    public SqlIdentifier {
        Objects.requireNonNull(text, "text");
    }

    public static SqlIdentifier parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new MicroOrmException("Invalid SQL identifier: blank");
        }
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '`' && trimmed.charAt(trimmed.length() - 1) == '`') {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            validate(inner);
            return new SqlIdentifier(inner, true);
        }
        validate(trimmed);
        return new SqlIdentifier(trimmed, false);
    }

    public static SqlIdentifier unquoted(String text) {
        validate(text);
        return new SqlIdentifier(text, false);
    }

    private static void validate(String text) {
        SqlGenerator.validateIdentifier(text, "identifier");
    }
}
