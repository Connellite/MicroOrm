package io.github.connellite.microorm.sql;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Named-parameter SQL for custom reads and updates executed through {@link io.github.connellite.microorm.session.Session}.
 * Parameter values are bound via ExtraLib — never concatenated into the SQL text.
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

    /** Creates a query from SQL with {@code :name} named placeholders. */
    public static Query of(String sql) {
        return new Query(sql);
    }

    /**
     * Binds a scalar parameter ({@code :name} in SQL).
     *
     * @return {@code this} for chaining
     * @throws IllegalArgumentException when the name is already bound
     */
    public Query set(String name, Object value) {
        putUnique(name);
        parameters.put(name, value);
        return this;
    }

    /** Binds several scalar parameters. Names must not overlap with prior bindings. */
    public Query setAll(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Binds a collection for {@code IN (:name)} expansion.
     *
     * @throws IllegalArgumentException when {@code values} is empty or the name is already bound
     */
    public Query setCollection(String name, Collection<?> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Collection parameter cannot be empty: " + name);
        }
        putUnique(name);
        collectionParameters.put(name, List.copyOf(values));
        return this;
    }

    /** SQL text with {@code :name} placeholders (unchanged after binding). */
    public String sql() {
        return sql;
    }

    /** Scalar parameters bound via {@link #set(String, Object)} / {@link #setAll(Map)}. */
    public Map<String, Object> parameters() {
        return Collections.unmodifiableMap(parameters);
    }

    /** Collection parameters bound via {@link #setCollection(String, Collection)}. */
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
