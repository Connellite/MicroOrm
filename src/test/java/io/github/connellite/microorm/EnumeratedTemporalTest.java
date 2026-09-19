package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Enumerated;
import io.github.connellite.microorm.annotation.EnumType;
import io.github.connellite.microorm.annotation.GeneratedValue;
import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Temporal;
import io.github.connellite.microorm.annotation.TemporalType;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumeratedTemporalTest {

    enum Status {
        OPEN, CLOSED
    }

    @Entity
    @Table(name = "enumerated_temporal_items")
    public static class EnumeratedTemporalItem {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private long id;

        private Status ordinalStatus;

        @Enumerated(EnumType.STRING)
        @Column(length = 32)
        private Status namedStatus;

        @Temporal(TemporalType.DATE)
        private Date day;

        @Temporal(TemporalType.TIME)
        private Date clock;

        @Temporal(TemporalType.TIMESTAMP)
        private Date occurredAt;

        @Temporal(TemporalType.TIMESTAMP)
        private Calendar calendar;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void persistsEnumeratedAndTemporalFields(DialectTestSupport.DialectFixture dialect) throws SQLException {
        try (Connection connection = dialect.openConnection()) {
            DialectTestSupport.dropTables(connection, "enumerated_temporal_items");
            MicroOrm orm = dialect.createOrm(connection).register(EnumeratedTemporalItem.class);
            try (Session session = orm.openSession()) {
                session.createEntity(EnumeratedTemporalItem.class);

                EnumeratedTemporalItem item = new EnumeratedTemporalItem();
                item.ordinalStatus = Status.CLOSED;
                item.namedStatus = Status.OPEN;
                item.day = java.sql.Date.valueOf(LocalDate.of(2026, 7, 13));
                item.clock = Time.valueOf(LocalTime.of(21, 50, 45));
                item.occurredAt = Timestamp.valueOf(LocalDateTime.of(2026, 7, 13, 21, 50, 45));
                item.calendar = new GregorianCalendar(2026, Calendar.JULY, 13, 21, 50, 45);
                session.insertRow(item);

                EnumeratedTemporalItem loaded = session.selectRow(EnumeratedTemporalItem.class, item.id);
                assertEquals(Status.CLOSED, loaded.ordinalStatus);
                assertEquals(Status.OPEN, loaded.namedStatus);
                assertEquals(LocalDate.of(2026, 7, 13), new java.sql.Date(loaded.day.getTime()).toLocalDate());
                assertEquals(LocalTime.of(21, 50, 45), new Time(loaded.clock.getTime()).toLocalTime());
                assertEquals(
                        LocalDateTime.of(2026, 7, 13, 21, 50, 45),
                        new Timestamp(loaded.occurredAt.getTime()).toLocalDateTime());
                assertEquals(item.calendar.getTimeInMillis(), loaded.calendar.getTimeInMillis());
            }
        }
    }

    private static Stream<DialectTestSupport.DialectFixture> dialects() {
        return DialectTestSupport.dialects();
    }
}
