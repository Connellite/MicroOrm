package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.exception.MicroOrmException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of {@link DynamicTable} definitions keyed by {@link DynamicTable#name()}.
 */
public final class DynamicTableRegistry {

    private final Map<String, DynamicTable> tables = new ConcurrentHashMap<>();

    /** Registers a table and returns it for chaining. Re-registration replaces the previous definition. */
    public DynamicTable register(DynamicTable table) {
        Objects.requireNonNull(table, "table");
        tables.put(table.name(), table);
        return table;
    }

    /** Returns a previously registered table. */
    public DynamicTable get(String name) {
        Objects.requireNonNull(name, "name");
        DynamicTable table = tables.get(name);
        if (table == null) {
            throw new MicroOrmException("Dynamic table not registered: " + name);
        }
        return table;
    }

    /** {@code true} when a table with the given registry name is registered. */
    public boolean isRegistered(String name) {
        return tables.containsKey(name);
    }
}
