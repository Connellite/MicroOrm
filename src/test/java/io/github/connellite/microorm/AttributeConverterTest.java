package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Convert;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.type.AttributeConverter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttributeConverterTest {

    record Money(String currency, BigDecimal amount) {
    }

    public static class MoneyConverter implements AttributeConverter<Money, String> {
        @Override
        public String convertToDatabaseColumn(Money attribute) {
            return attribute == null ? null : attribute.currency() + ":" + attribute.amount();
        }

        @Override
        public Money convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            String[] parts = dbData.split(":", 2);
            return new Money(parts[0], new BigDecimal(parts[1]));
        }
    }

    public static class WrongConverter implements AttributeConverter<String, Integer> {
        @Override
        public Integer convertToDatabaseColumn(String attribute) {
            return null;
        }

        @Override
        public String convertToEntityAttribute(Integer dbData) {
            return null;
        }
    }

    @Entity
    @Table(name = "converted_orders")
    public static class ConvertedOrder {
        @Id(autoIncrement = true)
        private long id;

        @Column(nullable = false, length = 64)
        @Convert(converter = MoneyConverter.class)
        private Money total;
    }

    @Entity
    @Table(name = "wrong_converted_orders")
    public static class WrongConvertedOrder {
        @Id(autoIncrement = true)
        private long id;

        @Convert(converter = WrongConverter.class)
        private Money total;
    }

    @Test
    void convertsAttributeToDatabaseColumnAndBack() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection).register(ConvertedOrder.class);
            try (Session session = orm.openSession()) {
                session.createEntity(ConvertedOrder.class);

                ConvertedOrder order = new ConvertedOrder();
                order.total = new Money("USD", new BigDecimal("12.34"));
                session.insertRow(order);

                assertEquals("USD:12.34", rawTotal(connection));

                ConvertedOrder loaded = session.selectRow(ConvertedOrder.class, order.id);
                assertEquals(new Money("USD", new BigDecimal("12.34")), loaded.total);

                Optional<ConvertedOrder> byConvertedCriterion = session.findOne(EntityQuery.of(ConvertedOrder.class)
                        .where(EntityQuery.field("total").eq(new Money("USD", new BigDecimal("12.34")))));
                assertEquals(order.id, byConvertedCriterion.orElseThrow().id);
            }
        }
    }

    @Test
    void rejectsConverterWhoseAttributeTypeDoesNotMatchField() {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(connection);

            assertThrows(MicroOrmException.class, () -> orm.register(WrongConvertedOrder.class));
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private static String rawTotal(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT total FROM converted_orders")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
