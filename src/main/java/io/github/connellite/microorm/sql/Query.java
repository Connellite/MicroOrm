package io.github.connellite.microorm.sql;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Named-parameter SQL for custom reads and updates. Values are bound via ExtraLib — never concatenated into the SQL text.
 */
public final class Query {

    private final String sql;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final Map<String, Collection<?>> collectionParameters = new LinkedHashMap<>();

    private Query(String sql) {
        this.sql = Objects.requireNonNull(sql, "sql");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("SQL cannot be blank");
        }
    }

    /** Creates a query from raw SQL with {@code :name} placeholders. */
    public static Query of(String sql) {
        return new Query(sql);
    }

    /** Binds a scalar parameter. Each name may be bound only once. */
    public Query set(String name, Object value) {
        putUnique(name);
        parameters.put(name, value);
        return this;
    }

    /** Binds multiple scalar parameters. */
    public Query setAll(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /** Binds a collection for IN-clause expansion ({@code :name} in SQL). */
    public Query setCollection(String name, Collection<?> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Collection parameter cannot be empty: " + name);
        }
        putUnique(name);
        collectionParameters.put(name, List.copyOf(values));
        return this;
    }

    public String sql() {
        return sql;
    }

    public Map<String, Object> parameters() {
        return Collections.unmodifiableMap(parameters);
    }

    public Map<String, Collection<?>> collectionParameters() {
        return Collections.unmodifiableMap(collectionParameters);
    }

    private void putUnique(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Parameter name cannot be blank");
        }
        if (parameters.containsKey(name) || collectionParameters.containsKey(name)) {
            throw new IllegalArgumentException("Parameter already bound: " + name);
        }
    }
}
