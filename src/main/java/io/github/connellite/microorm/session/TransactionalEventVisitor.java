package io.github.connellite.microorm.session;

/** Visitor for events published through {@link Session#publishEvent(Object)}. */
public interface TransactionalEventVisitor<E> {

    /** Called before {@link Session#commitTransaction()} calls JDBC {@code commit()}. */
    void beforeCommit(E event);

    /** Called after JDBC {@code commit()} succeeds. */
    void afterCommit(E event);

    /** Called after JDBC {@code rollback()} succeeds. */
    void afterRollback(E event);

    /** Called after successful commit or rollback listener phases. */
    void afterCompletion(E event);
}
