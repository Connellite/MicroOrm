package io.github.connellite.microorm.session;

/**
 * Transaction-phase event registration and publishing implemented by {@link Session}.
 */
public interface TransactionalEventSession {

    <E> TransactionalEventSession addTransactionalEventListener(
            Class<E> eventType,
            TransactionalEventVisitor<? super E> visitor);

    <E> TransactionalEventSession addTransactionalEventListener(
            Class<E> eventType,
            boolean fallbackExecution,
            TransactionalEventVisitor<? super E> visitor);

    void publishEvent(Object event);
}
