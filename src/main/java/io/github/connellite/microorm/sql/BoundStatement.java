package io.github.connellite.microorm.sql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SQL text with named parameters, produced by the built-in {@link SqlGenerator} or assembled manually.
 * Values are bound via ExtraLib — never concatenated into the SQL string.
 *
 * @param sql        SQL with {@code :name} placeholders
 * @param parameters bound parameter values (may be empty)
 */
public record BoundStatement(String sql, Map<String, Object> parameters) {

    public BoundStatement {
        Objects.requireNonNull(sql, "sql");
        parameters = parameters == null || parameters.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /** Creates a bound statement from SQL and named parameters. */
    public static BoundStatement of(String sql, Map<String, Object> params) {
        return new BoundStatement(sql, params);
    }
}
