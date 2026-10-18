package io.github.connellite.microorm.session;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Session-scoped identity map: {@code (entity class, id)} to the instance.
 * Strong references; callers drop entries with {@link #clear()} and {@code evict}.
 * An id can be known to exist without an instance after a pure exists check.
 */
final class EntityContext {

    private final Map<Key, Object> entities = new HashMap<>();
    private final Set<Key> knownIds = new HashSet<>();

    boolean contains(Class<?> type, Object id) {
        Key key = key(type, id);
        return entities.containsKey(key) || knownIds.contains(key);
    }

    @SuppressWarnings("unchecked")
    <T> T get(Class<?> type, Object id) {
        return (T) entities.get(key(type, id));
    }

    void put(Class<?> type, Object id, Object entity) {
        Key key = key(type, id);
        entities.put(key, entity);
        knownIds.remove(key);
    }

    void remember(Class<?> type, Object id) {
        Key key = key(type, id);
        if (!entities.containsKey(key)) {
            knownIds.add(key);
        }
    }

    void remove(Class<?> type, Object id) {
        Key key = key(type, id);
        entities.remove(key);
        knownIds.remove(key);
    }

    void evict(Class<?> type) {
        entities.keySet().removeIf(key -> key.type() == type);
        knownIds.removeIf(key -> key.type() == type);
    }

    void clear() {
        entities.clear();
        knownIds.clear();
    }

    int size() {
        return entities.size();
    }

    private static Key key(Class<?> type, Object id) {
        return new Key(Objects.requireNonNull(type, "entity class"), Objects.requireNonNull(id, "id"));
    }

    private record Key(Class<?> type, Object id) {
    }
}
