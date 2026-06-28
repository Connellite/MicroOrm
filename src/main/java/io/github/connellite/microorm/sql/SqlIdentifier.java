package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.exception.MicroOrmException;

import java.util.Objects;

/**
 * Logical SQL identifier plus quoting hint (Spring JPA / Hibernate backtick convention).
 * <ul>
 *   <li>{@code size} or unquoted names — physical case is chosen by the {@link io.github.connellite.microorm.dialect.Dialect}</li>
 *   <li>{@code `size`} in {@link io.github.connellite.microorm.annotation.Column#name()} — quoted SQL preserving case</li>
 * </ul>
 *
 * @param text   identifier text without surrounding backticks
 * @param quoted {@code true} when the name was declared with backticks and must be quoted in SQL
 */
public record SqlIdentifier(String text, boolean quoted) {

    public SqlIdentifier {
        Objects.requireNonNull(text, "text");
    }

    /**
     * Parses a name from {@link io.github.connellite.microorm.annotation.Entity#name()},
     * {@link io.github.connellite.microorm.annotation.Entity#schema()},
     * {@link io.github.connellite.microorm.annotation.Column#name()}, or {@link io.github.connellite.microorm.annotation.JoinColumn#name()}.
     */
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

    /** Creates an unquoted identifier (subject to dialect physical naming). */
    public static SqlIdentifier unquoted(String text) {
        validate(text);
        return new SqlIdentifier(text, false);
    }

    private static void validate(String text) {
        SqlGenerator.validateIdentifier(text, "identifier");
    }
}
