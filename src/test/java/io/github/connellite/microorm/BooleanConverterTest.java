package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Convert;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.GeneratedValue;
import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.type.NumericBooleanConverter;
import io.github.connellite.microorm.type.TrueFalseConverter;
import io.github.connellite.microorm.type.YesNoConverter;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BooleanConverterTest {

    @Test
    void trueFalseConverterMatchesHibernateEncoding() {
        TrueFalseConverter converter = new TrueFalseConverter();
        assertEquals('T', converter.convertToDatabaseColumn(true));
        assertEquals('F', converter.convertToDatabaseColumn(false));
        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals(true, converter.convertToEntityAttribute('T'));
        assertEquals(false, converter.convertToEntityAttribute('F'));
        assertNull(converter.convertToEntityAttribute('Y'));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void yesNoConverterMatchesHibernateEncoding() {
        YesNoConverter converter = new YesNoConverter();
        assertEquals('Y', converter.convertToDatabaseColumn(true));
        assertEquals('N', converter.convertToDatabaseColumn(false));
        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals(true, converter.convertToEntityAttribute('Y'));
        assertEquals(false, converter.convertToEntityAttribute('N'));
        assertNull(converter.convertToEntityAttribute('T'));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void numericBooleanConverterMatchesHibernateEncoding() {
        NumericBooleanConverter converter = new NumericBooleanConverter();
        assertEquals(1, converter.convertToDatabaseColumn(true));
        assertEquals(0, converter.convertToDatabaseColumn(false));
        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals(true, converter.convertToEntityAttribute(1));
        assertEquals(false, converter.convertToEntityAttribute(0));
        assertNull(converter.convertToEntityAttribute(2));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Entity
    @Table(name = "boolean_flags")
    public static class BooleanFlags {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private long id;

        @Convert(converter = TrueFalseConverter.class)
        private Boolean trueFalse;

        @Convert(converter = YesNoConverter.class)
        private Boolean yesNo;

        @Convert(converter = NumericBooleanConverter.class)
        private Boolean numeric;
    }

    @Test
    void roundTripsBooleanConvertersThroughSqlite() throws SQLException {
        DialectTestSupport.DialectFixture dialect = DialectTestSupport.sqlite();
        try (Connection connection = dialect.openConnection()) {
            MicroOrm orm = dialect.createOrm(connection).register(BooleanFlags.class);
            try (Session session = orm.openSession()) {
                session.createEntity(BooleanFlags.class);

                BooleanFlags flags = new BooleanFlags();
                flags.trueFalse = true;
                flags.yesNo = false;
                flags.numeric = true;
                session.insertRow(flags);

                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery(
                             "SELECT trueFalse, yesNo, numeric FROM boolean_flags")) {
                    rs.next();
                    assertEquals("T", rs.getString(1));
                    assertEquals("N", rs.getString(2));
                    assertEquals(1, rs.getInt(3));
                }

                BooleanFlags loaded = session.selectRow(BooleanFlags.class, flags.id);
                assertEquals(true, loaded.trueFalse);
                assertEquals(false, loaded.yesNo);
                assertEquals(true, loaded.numeric);
            }
        }
    }
}
