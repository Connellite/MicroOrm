package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Convert;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Immutable;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.query.EntityDelete;
import io.github.connellite.microorm.query.EntityInsert;
import io.github.connellite.microorm.query.EntitySelect;
import io.github.connellite.microorm.query.EntityUpdate;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.type.AttributeConverter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityCrudDslTest {

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

    @Entity
    @Table(name = "crud_dsl_items")
    public static class CrudItem {
        @Id(autoIncrement = true)
        private long id;

        @Column(nullable = false, length = 80)
        private String name;

        @Column(nullable = false)
        private boolean active;

        @Column(length = 64)
        @Convert(converter = MoneyConverter.class)
        private Money total;

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }

        public Money getTotal() {
            return total;
        }
    }

    @Entity
    @Immutable
    @Table(name = "crud_dsl_items")
    public static class ImmutableCrudItem {
        @Id(autoIncrement = true)
        private long id;

        private String name;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void crudDslExecutesInsertUpdateDeleteAcrossDialects(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "crud_dsl_items");
            MicroOrm orm = dialect.createOrm(connection).register(CrudItem.class, ImmutableCrudItem.class);
            try (Session session = orm.openSession()) {
                session.createEntity(CrudItem.class);

                assertEquals(1, session.execute(EntityInsert.into(CrudItem.class)
                        .value(CrudItem::getName, "first")
                        .value(CrudItem::isActive, true)
                        .value(CrudItem::getTotal, new Money("USD", new BigDecimal("12.34")))));

                CrudItem inserted = session.selectOne(EntitySelect.of(CrudItem.class)
                        .where(EntitySelect.field(CrudItem::getName).eq("first")));
                assertEquals("first", inserted.getName());
                assertEquals(new Money("USD", new BigDecimal("12.34")), inserted.getTotal());

                assertEquals(1, session.execute(EntityUpdate.of(CrudItem.class)
                        .set(CrudItem::getName, "renamed")
                        .set(CrudItem::getTotal, new Money("EUR", new BigDecimal("99.00")))
                        .where(EntitySelect.field(CrudItem::getTotal).eq(new Money("USD", new BigDecimal("12.34"))))));

                CrudItem updated = session.selectOne(EntitySelect.of(CrudItem.class)
                        .where(EntitySelect.field(CrudItem::getName).eq("renamed")));
                assertEquals(new Money("EUR", new BigDecimal("99.00")), updated.getTotal());

                assertEquals(1, session.execute(EntityInsert.into(CrudItem.class)
                        .value("name", "second")
                        .value("active", true)));
                assertEquals(2, session.execute(EntityUpdate.of(CrudItem.class)
                        .set(CrudItem::isActive, false)
                        .allRows()));
                assertFalse(session.selectOne(EntitySelect.of(CrudItem.class)
                        .where(EntitySelect.field(CrudItem::getName).eq("second"))).isActive());

                assertThrows(MicroOrmException.class, () -> session.execute(EntityDelete.from(CrudItem.class)));
                assertThrows(MicroOrmException.class, () -> session.execute(EntityUpdate.of(CrudItem.class)
                        .set(CrudItem::getId, 10L)
                        .where(EntitySelect.field(CrudItem::getName).eq("renamed"))));
                assertThrows(MicroOrmException.class, () -> session.execute(EntityUpdate.of(CrudItem.class)
                        .set("customer.name", "bad")
                        .where(EntitySelect.field(CrudItem::getName).eq("renamed"))));
                assertThrows(MicroOrmException.class, () -> session.execute(EntityUpdate.of(ImmutableCrudItem.class)
                        .set("name", "bad")
                        .allRows()));

                assertEquals(1, session.execute(EntityDelete.from(CrudItem.class)
                        .where(EntitySelect.field(CrudItem::getName).eq("renamed"))));
                assertEquals(1, session.selectRows(CrudItem.class).size());
                assertEquals(1, session.execute(EntityDelete.from(CrudItem.class).allRows()));
                assertEquals(List.of(), session.selectRows(CrudItem.class));
            }
        }
    }

    private static Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }
}
