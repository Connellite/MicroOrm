package io.github.connellite.microorm.sql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** SQL text with named parameters only (values bound via ExtraLib, never concatenated). */
public record BoundStatement(String sql, Map<String, Object> parameters) {

    public BoundStatement {
        Objects.requireNonNull(sql, "sql");
        parameters = parameters == null || parameters.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    public static BoundStatement of(String sql, Map<String, Object> params) {
        return new BoundStatement(sql, params);
    }
}
