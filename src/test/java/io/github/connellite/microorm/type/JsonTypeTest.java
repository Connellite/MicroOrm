package io.github.connellite.microorm.type;

import io.github.connellite.microorm.DialectTestSupport;
import io.github.connellite.microorm.MicroOrm;
import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Type;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTypeTest {

    @Entity
    @Table(name = "json_docs")
    public static class JsonDoc {
        @Id
        UUID id;

        @Type(JsonType.class)
        String payload;
    }

    @Entity
    @Table(name = "json_overrides")
    public static class JsonOverride {
        @Id
        UUID id;

        @Type(JsonType.class)
        @Column(sqlType = "VARCHAR(40)")
        String payload;
    }

    @Test
    void sqlTypeFollowsTheDialect() {
        JsonType type = new JsonType();
        assertEquals("jsonb", type.sqlType(PostgresDialect.getInstance()));
        assertEquals("NVARCHAR(MAX)", type.sqlType(MssqlDialect.getInstance()));
        assertEquals("JSON", type.sqlType(MysqlDialect.getInstance()));
        assertEquals("CLOB", type.sqlType(OracleDialect.getInstance()));
        assertEquals("TEXT", type.sqlType(SqliteDialect.getInstance()));
    }

    @Test
    void postgresBindUsesOther() {
        Object bound = new JsonType().toJdbc(PostgresDialect.getInstance(), "{\"a\":1}");
        JdbcTypedValue typed = assertInstanceOf(JdbcTypedValue.class, bound);
        assertEquals("{\"a\":1}", typed.value());
        assertEquals(Types.OTHER, typed.sqlType());
        assertEquals("{\"a\":1}", new JsonType().toJdbc(SqliteDialect.getInstance(), "{\"a\":1}"));
    }

    @Test
    void sqliteStoresJsonAsTextAndReadsItBack() throws SQLException {
        DialectTestSupport.DialectFixture dialect = DialectTestSupport.sqlite();
        try (Connection connection = dialect.openConnection()) {
            MicroOrm orm = dialect.createOrm(connection).register(JsonDoc.class);
            UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
            try (Session session = orm.openSession()) {
                session.createEntity(JsonDoc.class);
                JsonDoc doc = new JsonDoc();
                doc.id = id;
                doc.payload = "{\"a\":1}";
                session.insertRow(doc);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet types = statement.executeQuery(
                         "SELECT type FROM pragma_table_info('json_docs') WHERE name = 'payload'")) {
                assertTrue(types.next());
                assertEquals("TEXT", types.getString(1));
            }
            try (Session session = orm.openSession()) {
                assertEquals("{\"a\":1}", session.selectRow(JsonDoc.class, id).payload);
            }
        }
    }

    /** Test fixture: the library does not ship a JSON type. */
    public static final class JsonType implements JdbcType {

        @Override
        public String sqlType(Dialect dialect) {
            if (dialect instanceof PostgresDialect) {
                return "jsonb";
            }
            if (dialect instanceof MssqlDialect) {
                return "NVARCHAR(MAX)";
            }
            if (dialect instanceof OracleDialect) {
                return "CLOB";
            }
            if (dialect instanceof MysqlDialect) {
                return "JSON";
            }
            return "TEXT";
        }

        @Override
        public Object toJdbc(Dialect dialect, Object value) {
            if (value == null || !(dialect instanceof PostgresDialect)) {
                return value;
            }
            return new JdbcTypedValue(value, Types.OTHER);
        }
    }

    @Test
    void columnSqlTypeOverridesJdbcType() throws SQLException {
        DialectTestSupport.DialectFixture dialect = DialectTestSupport.sqlite();
        try (Connection connection = dialect.openConnection()) {
            MicroOrm orm = dialect.createOrm(connection).register(JsonOverride.class);
            try (Session session = orm.openSession()) {
                session.createEntity(JsonOverride.class);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet types = statement.executeQuery(
                         "SELECT type FROM pragma_table_info('json_overrides') WHERE name = 'payload'")) {
                assertTrue(types.next());
                assertEquals("VARCHAR(40)", types.getString(1));
            }
        }
    }
}
