package io.github.connellite.microorm.type;

import io.github.connellite.microorm.annotation.TemporalType;
import io.github.connellite.microorm.exception.MicroOrmException;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;

/**
 * Maps {@link Date} or {@link Calendar} to {@link java.sql.Date}, {@link Time}, or {@link Timestamp}.
 */
public final class TemporalAttributeConverter implements AttributeConverter<Object, Object> {

    private final TemporalType temporalType;
    private final boolean calendarAttribute;

    public TemporalAttributeConverter(TemporalType temporalType, Class<?> attributeType) {
        this.temporalType = temporalType;
        this.calendarAttribute = attributeType == Calendar.class;
    }

    @Override
    public Object convertToDatabaseColumn(Object attribute) {
        if (attribute == null) {
            return null;
        }
        long millis = millis(attribute);
        return switch (temporalType) {
            case DATE -> new java.sql.Date(millis);
            case TIME -> new Time(millis);
            case TIMESTAMP -> new Timestamp(millis);
        };
    }

    @Override
    public Object convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        long millis = millis(dbData);
        if (calendarAttribute) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(millis);
            return calendar;
        }
        return new Date(millis);
    }

    private static long millis(Object value) {
        if (value instanceof Calendar calendar) {
            return calendar.getTimeInMillis();
        }
        if (value instanceof Date date) {
            return date.getTime();
        }
        throw new MicroOrmException("Unsupported temporal value: " + value.getClass().getName());
    }
}
