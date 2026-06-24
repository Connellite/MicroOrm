package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.connection.SpringJdbcSupport;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSessionTest {

    @Entity(name = "spring_widgets")
    public static class Widget {
        @Id
        private UUID id;

        @Column(nullable = false)
        private String name;

        public Widget() {
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    void sessionCloseInsideSpringTransactionCommits() throws SQLException {
        SpringFixture fixture = newSpringFixture();
        fixture.createSchema();

        UUID id = fixture.inTransaction(session -> {
            Widget widget = new Widget();
            widget.setName("spring-tx");
            session.insertRow(widget);
            return widget.getId();
        });

        try (Session verify = fixture.orm.openSession()) {
            Widget loaded = verify.selectRow(Widget.class, id);
            assertNotNull(loaded);
            assertEquals("spring-tx", loaded.getName());
        }
    }

    @Test
    void springTransactionRollsBackOnException() throws SQLException {
        SpringFixture fixture = newSpringFixture();
        fixture.createSchema();

        UUID[] id = new UUID[1];
        assertThrows(RuntimeException.class, () -> fixture.tx.execute(status -> {
            try (Session session = fixture.orm.openSession()) {
                Widget widget = new Widget();
                widget.setName("doomed");
                session.insertRow(widget);
                id[0] = widget.getId();
                throw new RuntimeException("boom");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }));

        try (Session verify = fixture.orm.openSession()) {
            assertNull(verify.selectRow(Widget.class, id[0]));
        }
    }

    @Test
    void springTransactionRollsBackWhenMarkedRollbackOnly() throws SQLException {
        SpringFixture fixture = newSpringFixture();
        fixture.createSchema();

        UUID[] id = new UUID[1];
        fixture.tx.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                try (Session session = fixture.orm.openSession()) {
                    Widget widget = new Widget();
                    widget.setName("rollback-only");
                    session.insertRow(widget);
                    id[0] = widget.getId();
                    status.setRollbackOnly();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        try (Session verify = fixture.orm.openSession()) {
            assertNull(verify.selectRow(Widget.class, id[0]));
        }
    }

    @Test
    void springTransactionRollsBackAllInsertsOnLateException() throws SQLException {
        SpringFixture fixture = newSpringFixture();
        fixture.createSchema();

        UUID baselineId = fixture.inTransaction(session -> {
            Widget baseline = new Widget();
            baseline.setName("baseline");
            session.insertRow(baseline);
            return baseline.getId();
        });

        UUID[] firstId = new UUID[1];
        UUID[] secondId = new UUID[1];
        assertThrows(RuntimeException.class, () -> fixture.tx.execute(status -> {
            try (Session session = fixture.orm.openSession()) {
                Widget first = new Widget();
                first.setName("first");
                session.insertRow(first);
                firstId[0] = first.getId();

                Widget second = new Widget();
                second.setName("second");
                session.insertRow(second);
                secondId[0] = second.getId();

                throw new RuntimeException("fail after second insert");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }));

        try (Session verify = fixture.orm.openSession()) {
            assertEquals("baseline", verify.selectRow(Widget.class, baselineId).getName());
            assertNull(verify.selectRow(Widget.class, firstId[0]));
            assertNull(verify.selectRow(Widget.class, secondId[0]));
        }
    }

    @Test
    void multipleSessionsShareSpringTransaction() throws SQLException {
        SpringFixture fixture = newSpringFixture();
        fixture.createSchema();

        UUID id = fixture.inTransaction(ignored -> {
            UUID insertedId;
            try (Session insert = fixture.orm.openSession()) {
                Widget widget = new Widget();
                widget.setName("shared-connection");
                insert.insertRow(widget);
                insertedId = widget.getId();
            }
            try (Session read = fixture.orm.openSession()) {
                Widget loaded = read.selectRow(Widget.class, insertedId);
                assertNotNull(loaded);
                assertEquals("shared-connection", loaded.getName());
            }
            return insertedId;
        });

        try (Session verify = fixture.orm.openSession()) {
            assertEquals("shared-connection", verify.selectRow(Widget.class, id).getName());
        }
    }

    @Test
    void springManagedConnectionIsDetected() {
        SpringFixture fixture = newSpringFixture();
        fixture.inTransaction(session -> {
            assertTrue(SpringJdbcSupport.isTransactionManagedConnection(session.connection()));
            return null;
        });
    }

    @Test
    void plainConnectionIsNotDetectedAsSpringManaged() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            assertFalse(SpringJdbcSupport.isTransactionManagedConnection(connection));
        }
    }

    @Test
    void sessionCloseWithoutSpringProxyRollsBackOpenTransaction() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(connection).register(Widget.class);
            UUID id;
            try (Session setup = orm.openSession()) {
                setup.createEntity(Widget.class);
                setup.beginTransaction();
                Widget widget = new Widget();
                widget.setName("rolled-back");
                setup.insertRow(widget);
                id = widget.getId();
            }

            try (Session verify = orm.openSession()) {
                assertNull(verify.selectRow(Widget.class, id));
            }
        }
    }

    @Test
    void localRollbackTransactionStillWorksWithoutSpringProxy() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(connection).register(Widget.class);
            UUID id;
            try (Session session = orm.openSession()) {
                session.createEntity(Widget.class);
                session.beginTransaction();
                Widget widget = new Widget();
                widget.setName("explicit-rollback");
                session.insertRow(widget);
                id = widget.getId();
                session.rollbackTransaction();
            }

            try (Session verify = orm.openSession()) {
                assertNull(verify.selectRow(Widget.class, id));
            }
        }
    }

    private static SpringFixture newSpringFixture() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        dataSource.setSuppressClose(true);
        TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(dataSource);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(proxy);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Orm orm = Orm.sqlite(proxy).register(Widget.class);
        return new SpringFixture(orm, tx);
    }

    @FunctionalInterface
    private interface SessionAction<T> {
        T run(Session session) throws SQLException;
    }

    private static final class SpringFixture {
        private final Orm orm;
        private final TransactionTemplate tx;

        private SpringFixture(Orm orm, TransactionTemplate tx) {
            this.orm = orm;
            this.tx = tx;
        }

        void createSchema() throws SQLException {
            try (Session session = orm.openSession()) {
                session.createEntity(Widget.class);
            }
        }

        <T> T inTransaction(SessionAction<T> action) {
            return tx.execute(status -> {
                try (Session session = orm.openSession()) {
                    return action.run(session);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
