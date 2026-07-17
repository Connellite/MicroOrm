package io.github.connellite.microorm;

import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.session.SimpleTransactionalEventVisitor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionTransactionalEventTest {

    private record TestEvent(String value) {
    }

    @Test
    void dispatchesPublishedEventsAroundCommit() throws SQLException {
        List<String> calls = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            try (Session session = orm.openSession()) {
                session.addTransactionalEventListener(TestEvent.class, new SimpleTransactionalEventVisitor<>() {
                    @Override
                    public void beforeCommit(TestEvent event) {
                        calls.add("beforeCommit:" + event.value());
                    }

                    @Override
                    public void afterCommit(TestEvent event) {
                        calls.add("afterCommit:" + event.value());
                    }

                    @Override
                    public void afterCompletion(TestEvent event) {
                        calls.add("afterCompletion:" + event.value());
                    }
                });

                session.beginTransaction();
                session.publishEvent(new TestEvent("created"));
                session.commitTransaction();
            }
        }

        assertEquals(List.of(
                "beforeCommit:created",
                "afterCommit:created",
                "afterCompletion:created"), calls);
    }

    @Test
    void dispatchesPublishedEventsAroundRollback() throws SQLException {
        List<String> calls = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            try (Session session = orm.openSession()) {
                session.addTransactionalEventListener(TestEvent.class, new SimpleTransactionalEventVisitor<>() {
                    @Override
                    public void afterCommit(TestEvent event) {
                        calls.add("afterCommit:" + event.value());
                    }

                    @Override
                    public void afterRollback(TestEvent event) {
                        calls.add("afterRollback:" + event.value());
                    }

                    @Override
                    public void afterCompletion(TestEvent event) {
                        calls.add("afterCompletion:" + event.value());
                    }
                });

                session.beginTransaction();
                session.publishEvent(new TestEvent("rolled-back"));
                session.rollbackTransaction();
            }
        }

        assertEquals(List.of(
                "afterRollback:rolled-back",
                "afterCompletion:rolled-back"), calls);
    }

    @Test
    void dispatchesFallbackListenersImmediatelyOutsideTransaction() throws SQLException {
        List<String> calls = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);
            try (Session session = orm.openSession()) {
                session.addTransactionalEventListener(TestEvent.class, new SimpleTransactionalEventVisitor<>() {
                    @Override
                    public void afterCommit(TestEvent event) {
                        calls.add("ignored:" + event.value());
                    }
                });
                session.addTransactionalEventListener(TestEvent.class, true, new SimpleTransactionalEventVisitor<>() {
                    @Override
                    public void afterCommit(TestEvent event) {
                        calls.add("fallback:" + event.value());
                    }
                });

                session.publishEvent(new TestEvent("outside"));
            }
        }

        assertEquals(List.of("fallback:outside"), calls);
    }
}
