package io.github.connellite.microorm.relation;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.OneToManyField;

import java.util.List;

/**
 * Session-scoped loader for relation associations.
 * Implemented by {@link io.github.connellite.microorm.session.SessionLazyContext}; not intended
 * for application code — passed internally when hydrating entities.
 */
public interface LazyLoadContext {

    /** Throws if the owning {@link io.github.connellite.microorm.session.Session} has been closed. */
    void ensureOpen();

    /**
     * Loads a single entity by primary key (used by {@link LazyRef#get()} and eager hydration).
     *
     * @return the row, or {@code null} when no row matches
     */
    <T> T loadById(Class<T> type, Object id);

    /**
     * Loads all child entities for a collection relation (inverse side of {@code mappedBy}).
     */
    <T> List<T> loadCollection(OneToManyField relation, Object ownerId);

    /**
     * Validates that lazy loading runs inside an open session.
     *
     * @throws MicroOrmException when {@code context} is {@code null} (reference created for persist only)
     */
    static void ensureOpen(LazyLoadContext context) {
        if (context == null) {
            throw new MicroOrmException("Lazy association loaded outside an open Session");
        }
        context.ensureOpen();
    }
}
