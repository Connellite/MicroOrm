package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.MicroOrm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicSessionCacheTest {

    private Connection raw;
    private final List<String> selectSql = new ArrayList<>();
    private MicroOrm orm;

    @BeforeEach
    void open() throws SQLException {
        raw = DriverManager.getConnection("jdbc:sqlite::memory:");
        orm = MicroOrm.sqlite(counting(raw, selectSql));
        orm.dynamicRegistry().register(table("docs"));
        orm.dynamicRegistry().register(table("folders"));
        try (DynamicSession session = orm.openDynamicSession()) {
            session.createTable("docs");
            session.createTable("folders");
        }
        selectSql.clear();
    }

    @AfterEach
    void close() throws SQLException {
        raw.close();
    }

    @Test
    void repeatedSelectByPrimaryKeyReturnsTheSameMap() throws SQLException {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        Map<String, Object> row = row(id, "one");
        try (DynamicSession session = orm.openDynamicSession()) {
            session.insert("docs", row);

            selectSql.clear();
            assertSame(row, session.select("docs", Map.of("id", id)).get(0));
            assertEquals(0, selectSql.size());

            session.clear();
            Map<String, Object> first = session.select("docs", Map.of("id", id)).get(0);
            Map<String, Object> second = session.select("docs", Map.of("id", id)).get(0);
            assertSame(first, second);
            assertEquals("one", second.get("name"));
            assertEquals(1, selectSql.size());
            assertEquals(1, session.cachedCount());
        }
    }

    @Test
    void updateByPrimaryKeyMutatesTheCachedMapAndDeleteRereads() throws SQLException {
        UUID id = UUID.fromString("22222222-2222-4222-8222-222222222222");
        Map<String, Object> row = row(id, "before");
        try (DynamicSession session = orm.openDynamicSession()) {
            session.insert("docs", row);
            session.update("docs", Map.of("name", "after"), Map.of("id", id));
            selectSql.clear();
            assertSame(row, session.select("docs", Map.of("id", id)).get(0));
            assertEquals("after", row.get("name"));
            assertEquals(0, selectSql.size());

            session.evict("docs", id);
            assertEquals("after", session.select("docs", Map.of("id", id)).get(0).get("name"));
            assertEquals(1, selectSql.size());

            session.delete("docs", Map.of("id", id));
            assertEquals(0, session.cachedCount());
            assertTrue(session.select("docs", Map.of("id", id)).isEmpty());
            assertEquals(2, selectSql.size());
        }
    }

    @Test
    void evictOneTableLeavesTheOther() throws SQLException {
        UUID docId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        UUID folderId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        Map<String, Object> doc = row(docId, "doc");
        Map<String, Object> folder = row(folderId, "inbox");
        try (DynamicSession session = orm.openDynamicSession()) {
            session.insert("docs", doc);
            session.insert("folders", folder);
            session.evict("docs");
            selectSql.clear();
            assertSame(folder, session.select("folders", Map.of("id", folderId)).get(0));
            assertEquals(0, selectSql.size());
            assertTrue(session.exists("folders", Map.of("id", folderId)));
            assertEquals(0, selectSql.size());
        }
    }

    @Test
    void nonPrimaryKeyUpdateDropsTheTableCache() throws SQLException {
        UUID id = UUID.fromString("55555555-5555-4555-8555-555555555555");
        Map<String, Object> row = row(id, "before");
        try (DynamicSession session = orm.openDynamicSession()) {
            session.insert("docs", row);
            session.update("docs", Map.of("name", "bulk"), Map.of("id", id, "name", "before"));
            assertEquals(0, session.cachedCount());
            selectSql.clear();
            Map<String, Object> loaded = session.select("docs", Map.of("id", id)).get(0);
            assertEquals("bulk", loaded.get("name"));
            assertEquals(1, selectSql.size());
        }
    }

    @Test
    void streamDoesNotFillTheContext() throws SQLException {
        try (DynamicSession session = orm.openDynamicSession()) {
            for (int i = 0; i < 5; i++) {
                session.insert("docs", row(UUID.randomUUID(), "row-" + i));
            }
            session.clear();
            try (var rows = session.streamRows(DynamicSelect.from("docs"))) {
                assertEquals(5, rows.toList().size());
            }
            assertEquals(0, session.cachedCount());
            assertEquals(5, session.selectAll("docs").size());
            assertEquals(0, session.cachedCount());
        }
    }

    private static DynamicTable table(String name) {
        return DynamicTable.builder(name)
                .column("id", LogicalType.UUID, Column.Builder::primaryKey)
                .column("name", LogicalType.STRING, Column.Builder::notNull)
                .build();
    }

    private static Map<String, Object> row(UUID id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private static Connection counting(Connection delegate, List<String> selectSql) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
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
                });
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
