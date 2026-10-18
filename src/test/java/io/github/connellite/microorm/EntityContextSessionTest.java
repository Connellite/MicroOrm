package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.JoinTable;
import io.github.connellite.microorm.annotation.ManyToMany;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityContextSessionTest {

    @Entity
    @Table(name = "l1_items")
    static class Item {
        @Id
        UUID id;
        @Column(nullable = false)
        String name;

        Item() {
        }
    }

    @Entity
    @Table(name = "l1_folders")
    static class Folder {
        @Id
        UUID id;
        @Column(nullable = false)
        String name;

        Folder() {
        }
    }

    @Entity
    @Table(name = "l1_documents")
    static class Document {
        @Id
        UUID id;
        @Column(nullable = false)
        String title;
        @ManyToMany
        @JoinTable(
                name = "l1_document_folders",
                joinColumns = @JoinColumn(name = "document_id"),
                inverseJoinColumns = @JoinColumn(name = "folder_id"))
        LazyCollection<Folder> folders;

        Document() {
        }
    }

    private Connection raw;
    private final List<String> selectSql = new ArrayList<>();
    private MicroOrm orm;

    @BeforeEach
    void open() throws SQLException {
        raw = DriverManager.getConnection("jdbc:sqlite::memory:");
        orm = MicroOrm.sqlite(counting(raw, selectSql));
        try (Session session = orm.openSession()) {
            session.createEntity(Item.class);
            session.createEntity(Folder.class);
            session.createEntity(Document.class);
        }
        selectSql.clear();
    }

    @AfterEach
    void close() throws SQLException {
        raw.close();
    }

    @Test
    void repeatedSelectRowReturnsTheSameInstanceWithoutAnotherSelect() throws SQLException {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        try (Session session = orm.openSession()) {
            Item item = new Item();
            item.id = id;
            item.name = "one";
            session.insertRow(item);

            selectSql.clear();
            assertSame(item, session.selectRow(Item.class, id));
            assertEquals(0, selectSql.size());

            session.clear();
            Item first = session.selectRow(Item.class, id);
            Item second = session.selectRow(Item.class, id);
            assertSame(first, second);
            assertEquals(1, selectSql.size());
            assertEquals(1, session.cachedCount());
        }
    }

    @Test
    void updateKeepsTheInstanceAndDeleteDropsItBeforeTheNextRead() throws SQLException {
        UUID id = UUID.fromString("22222222-2222-4222-8222-222222222222");
        try (Session session = orm.openSession()) {
            Item item = new Item();
            item.id = id;
            item.name = "before";
            session.insertRow(item);
            session.selectRow(Item.class, id);

            item.name = "after";
            session.updateRow(item);
            selectSql.clear();
            assertSame(item, session.selectRow(Item.class, id));
            assertEquals(0, selectSql.size());

            session.evict(item);
            Item reloaded = session.selectRow(Item.class, id);
            assertEquals("after", reloaded.name);
            assertEquals(1, selectSql.size());

            session.deleteRow(reloaded);
            assertEquals(0, session.cachedCount());
            assertNull(session.selectRow(Item.class, id));
            assertEquals(2, selectSql.size());
        }
    }

    @Test
    void cachedFolderIsReusedAcrossOwnerInserts() throws SQLException {
        UUID folderId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        try (Session session = orm.openSession()) {
            Folder folder = new Folder();
            folder.id = folderId;
            folder.name = "inbox";
            session.insertRow(folder);

            selectSql.clear();
            session.insertRow(document("44444444-4444-4444-8444-444444444444", folder));
            session.insertRow(document("55555555-5555-4555-8555-555555555555", folder));
            assertEquals(0, selectsOn("l1_folders"));

            Document loaded = session.selectRow(Document.class, UUID.fromString("44444444-4444-4444-8444-444444444444"));
            assertSame(folder, loaded.folders.get().get(0));
            assertSame(folder, session.selectRow(Folder.class, folderId));

            session.evict(loaded);
            assertSame(folder, session.selectRow(Folder.class, folderId));
            int folderSelects = selectsOn("l1_folders");
            assertTrue(session.existsById(Folder.class, folderId));
            assertEquals(folderSelects, selectsOn("l1_folders"));
        }
    }

    @Test
    void secondOwnerInsertSkipsTheTargetExistsQuery() throws SQLException {
        UUID folderId = UUID.fromString("66666666-6666-4666-8666-666666666666");
        try (Session setup = orm.openSession()) {
            Folder folder = new Folder();
            folder.id = folderId;
            folder.name = "archive";
            setup.insertRow(folder);
        }

        try (Session session = orm.openSession()) {
            Folder detached = new Folder();
            detached.id = folderId;
            detached.name = "not the cached name";
            selectSql.clear();
            session.insertRow(document("77777777-7777-4777-8777-777777777771", detached));
            assertEquals(1, selectsOn("l1_folders"));
            session.insertRow(document("77777777-7777-4777-8777-777777777772", detached));
            assertEquals(1, selectsOn("l1_folders"));

            Folder loaded = session.selectRow(Folder.class, folderId);
            assertEquals("archive", loaded.name);
            session.evict(Document.class);
            Document first = session.selectRow(Document.class, UUID.fromString("77777777-7777-4777-8777-777777777771"));
            assertSame(loaded, first.folders.get().get(0));
        }
    }

    @Test
    void clearForcesTheNextReadToQuery() throws SQLException {
        UUID id = UUID.fromString("88888888-8888-4888-8888-888888888888");
        try (Session session = orm.openSession()) {
            Item item = new Item();
            item.id = id;
            item.name = "cached";
            session.insertRow(item);
            assertSame(item, session.selectRow(Item.class, id));

            session.clear();
            assertEquals(0, session.cachedCount());
            selectSql.clear();
            Item reloaded = session.selectRow(Item.class, id);
            assertEquals("cached", reloaded.name);
            assertEquals(1, selectSql.size());
        }
    }

    @Test
    void streamDoesNotFillTheContext() throws SQLException {
        try (Session session = orm.openSession()) {
            for (int i = 0; i < 5; i++) {
                Item item = new Item();
                item.id = UUID.randomUUID();
                item.name = "row-" + i;
                session.insertRow(item);
            }
            session.clear();
            try (var rows = session.streamRows(Item.class)) {
                assertEquals(5, rows.toList().size());
            }
            assertEquals(0, session.cachedCount());
        }
    }

    @Test
    void missingTargetWithoutCascadeIsRejected() throws SQLException {
        try (Session session = orm.openSession()) {
            Folder missing = new Folder();
            missing.id = UUID.fromString("99999999-9999-4999-8999-999999999999");
            missing.name = "gone";
            Document document = document("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", missing);
            MicroOrmException error = assertThrows(MicroOrmException.class, () -> session.insertRow(document));
            assertTrue(error.getMessage().contains("transient instance must be saved"));
            assertNull(session.selectRow(Document.class, document.id));
        }
    }

    private static Document document(String id, Folder folder) {
        Document document = new Document();
        document.id = UUID.fromString(id);
        document.title = "doc";
        document.folders = LazyCollection.of(List.of(folder));
        return document;
    }

    private int selectsOn(String table) {
        String needle = table.toLowerCase();
        return (int) selectSql.stream().filter(sql -> sql.toLowerCase().contains(needle)).count();
    }

    private static Connection counting(Connection delegate, List<String> selectSql) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().startsWith("prepare")) {
                String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : "";
                Object statement = invoke(delegate, method, args);
                return Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[]{PreparedStatement.class},
                        (statementProxy, statementMethod, statementArgs) -> {
                            if ("executeQuery".equals(statementMethod.getName()) && isSelect(sql)) {
                                selectSql.add(sql);
                            }
                            if ("unwrap".equals(statementMethod.getName())
                                    && statementArgs != null
                                    && statementArgs[0] instanceof Class<?> type
                                    && type.isInstance(statement)) {
                                return statement;
                            }
                            return invoke(statement, statementMethod, statementArgs);
                        });
            }
            if ("unwrap".equals(method.getName())
                    && args != null
                    && args[0] instanceof Class<?> type
                    && type.isInstance(delegate)) {
                return delegate;
            }
            return invoke(delegate, method, args);
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler);
    }

    private static boolean isSelect(String sql) {
        String trimmed = sql.stripLeading();
        return trimmed.regionMatches(true, 0, "select", 0, 6) || trimmed.regionMatches(true, 0, "with", 0, 4);
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
