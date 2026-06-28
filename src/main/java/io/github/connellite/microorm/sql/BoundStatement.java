package io.github.connellite.microorm.sql;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SQL text with named parameters, produced by the built-in {@link SqlGenerator} or assembled manually.
 * Values are bound via ExtraLib — never concatenated into the SQL string.
 *
 * @param sql                  SQL with {@code :name} placeholders
 * @param parameters           scalar bound parameter values (may be empty)
 * @param collectionParameters collection bound parameter values expanded by ExtraLib
 */
public record BoundStatement(
        String sql,
        Map<String, Object> parameters,
        Map<String, Collection<?>> collectionParameters) {

    public BoundStatement {
        Objects.requireNonNull(sql, "sql");
        parameters = parameters == null || parameters.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        collectionParameters = collectionParameters == null || collectionParameters.isEmpty()
                ? Map.of()
                : copyCollectionParameters(collectionParameters);
    }

    public BoundStatement(String sql, Map<String, Object> parameters) {
        this(sql, parameters, Map.of());
    }

    /** Creates a bound statement from SQL and named parameters. */
    public static BoundStatement of(String sql, Map<String, Object> params) {
        return new BoundStatement(sql, params);
    }

    /** Creates a bound statement from SQL, scalar parameters, and collection parameters. */
    public static BoundStatement of(
            String sql,
            Map<String, Object> params,
            Map<String, Collection<?>> collectionParams) {
        return new BoundStatement(sql, params, collectionParams);
    }

    private static Map<String, Collection<?>> copyCollectionParameters(Map<String, Collection<?>> source) {
        Map<String, Collection<?>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Collection<?>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
