package io.github.connellite.microorm.session;

/**
 * Convenience base visitor for transaction events.
 * <p>
 * Subclasses override only the phases they need, similar to {@link java.nio.file.SimpleFileVisitor}.
 */
public class SimpleTransactionalEventVisitor<E> implements TransactionalEventVisitor<E> {

    @Override
    public void beforeCommit(E event) {
    }

    @Override
    public void afterCommit(E event) {
    }

    @Override
    public void afterRollback(E event) {
    }

    @Override
    public void afterCompletion(E event) {
    }
}
