package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.connection.SpringJdbcSupport;
import io.github.connellite.microorm.session.Session;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSessionTest {

    @Getter
    @Entity
    @Table(name = "spring_widgets")
    public static class Widget {
        @Id
        private UUID id;

        @Setter
        @Column(nullable = false)
        private String name;

        public Widget() {
        }

    }

    @Test
    void transactionTemplateUsesPlainDataSourceWithoutTransactionAwareProxy() throws SQLException {
        try (SpringFixture fixture = newSpringFixture()) {
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
    }

    @Test
    void transactionalAnnotatedServiceUsesPlainDataSourceWithoutTransactionAwareProxy() throws Exception {
        try (SpringFixture fixture = newSpringFixture()) {
            fixture.createSchema();
            TransactionalWidgetService service = new TransactionalWidgetService(fixture.orm);

            UUID id = fixture.invokeTransactional(
                    TransactionalWidgetService.class.getDeclaredMethod("createWidget", String.class),
                    () -> service.createWidget("annotated-tx"));

            try (Session verify = fixture.orm.openSession()) {
                Widget loaded = verify.selectRow(Widget.class, id);
                assertNotNull(loaded);
                assertEquals("annotated-tx", loaded.getName());
            }
        }
    }

    @Test
    void springTransactionRollsBackOnException() throws SQLException {
        try (SpringFixture fixture = newSpringFixture()) {
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
    }

    @Test
    void springTransactionRollsBackWhenMarkedRollbackOnly() throws SQLException {
        try (SpringFixture fixture = newSpringFixture()) {
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
    }

    @Test
    void springTransactionRollsBackAllInsertsOnLateException() throws SQLException {
        try (SpringFixture fixture = newSpringFixture()) {
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
    }

    @Test
    void multipleSessionsShareSpringTransaction() throws SQLException {
        try (SpringFixture fixture = newSpringFixture()) {
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
    }

    @Test
    void springTransactionalConnectionIsDetectedForPlainDataSource() throws SQLException {
        try (SpringFixture fixture = newSpringFixture()) {
            fixture.inTransaction(session -> {
                assertFalse(SpringJdbcSupport.isTransactionManagedConnection(session.connection()));
                assertTrue(SpringJdbcSupport.isActualTransactionActive());
                assertTrue(SpringJdbcSupport.isConnectionTransactional(session.connection(), fixture.dataSource));
                return null;
            });
        }
    }

    @Test
    void transactionAwareProxyConnectionIsStillDetected() throws SQLException {
        try (SpringFixture fixture = newSpringProxyFixture()) {
            fixture.inTransaction(session -> {
                assertTrue(SpringJdbcSupport.isTransactionManagedConnection(session.connection()));
                return null;
            });
        }
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
            MicroOrm orm = MicroOrm.sqlite(connection).register(Widget.class);
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
            MicroOrm orm = MicroOrm.sqlite(connection).register(Widget.class);
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

    private static SpringFixture newSpringFixture() throws SQLException {
        String url = "jdbc:sqlite:file:spring-" + UUID.randomUUID() + "?mode=memory&cache=shared";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        Connection anchor = dataSource.getConnection();
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        MicroOrm orm = MicroOrm.sqlite(dataSource).register(Widget.class);
        return new SpringFixture(dataSource, orm, tx, anchor);
    }

    private static SpringFixture newSpringProxyFixture() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        dataSource.setSuppressClose(true);
        TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(dataSource);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(proxy);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        MicroOrm orm = MicroOrm.sqlite(proxy).register(Widget.class);
        return new SpringFixture(proxy, orm, tx, null);
    }

    @FunctionalInterface
    private interface SessionAction<T> {
        T run(Session session) throws SQLException;
    }

    private record SpringFixture(DataSource dataSource, MicroOrm orm, TransactionTemplate tx,
                                 Connection anchor) implements AutoCloseable {

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

        <T> T invokeTransactional(Method method, Supplier<T> action) {
            assertNotNull(method.getAnnotation(Transactional.class));
            return tx.execute(status -> action.get());
        }

        @Override
        public void close() throws SQLException {
            if (anchor != null) {
                anchor.close();
            }
        }
    }

    private record TransactionalWidgetService(MicroOrm orm) {
        @Transactional
        UUID createWidget(String name) {
            try (Session session = orm.openSession()) {
                Widget widget = new Widget();
                widget.setName(name);
                session.insertRow(widget);
                return widget.getId();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
