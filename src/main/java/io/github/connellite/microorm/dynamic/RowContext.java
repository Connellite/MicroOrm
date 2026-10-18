package io.github.connellite.microorm.dynamic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Session-scoped identity map for dynamic rows: {@code (registry table name, id)} to the row map.
 * Strong references. A pure exists check can remember an id without storing the row.
 */
final class RowContext {

    private final Map<Key, Map<String, Object>> rows = new HashMap<>();
    private final Set<Key> knownIds = new HashSet<>();

    boolean contains(String tableName, Object id) {
        Key key = key(tableName, id);
        return rows.containsKey(key) || knownIds.contains(key);
    }

    Map<String, Object> get(String tableName, Object id) {
        return rows.get(key(tableName, id));
    }

    void put(String tableName, Object id, Map<String, Object> row) {
        Key key = key(tableName, id);
        rows.put(key, row);
        knownIds.remove(key);
    }

    void remember(String tableName, Object id) {
        Key key = key(tableName, id);
        if (!rows.containsKey(key)) {
            knownIds.add(key);
        }
    }

    void remove(String tableName, Object id) {
        Key key = key(tableName, id);
        rows.remove(key);
        knownIds.remove(key);
    }

    void evict(String tableName) {
        rows.keySet().removeIf(key -> key.tableName().equals(tableName));
        knownIds.removeIf(key -> key.tableName().equals(tableName));
    }

    void clear() {
        rows.clear();
        knownIds.clear();
    }

    int size() {
        return rows.size();
    }

    private static Key key(String tableName, Object id) {
        return new Key(Objects.requireNonNull(tableName, "table name"), Objects.requireNonNull(id, "id"));
    }

    private record Key(String tableName, Object id) {
    }
}
