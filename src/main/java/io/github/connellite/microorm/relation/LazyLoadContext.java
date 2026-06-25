package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.mapping.OneToManyField;

import java.util.List;

/** Session-scoped loader for lazy associations. */
public interface LazyLoadContext {

    void ensureOpen();

    <T> T loadById(Class<T> type, Object id);

    <T> List<T> loadCollection(OneToManyField relation, Object ownerId);

    static void ensureOpen(LazyLoadContext context) {
        if (context == null) {
            throw new MicroOrmException("Lazy association loaded outside an open Session");
        }
        context.ensureOpen();
    }
}
