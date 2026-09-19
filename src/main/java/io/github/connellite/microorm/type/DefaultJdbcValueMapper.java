package io.github.connellite.microorm.type;

import io.github.connellite.exception.TypeCoercionException;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.util.TypeCoercionUtil;
import io.github.connellite.util.UuidUtil;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

/**
 * Default {@link JdbcValueMapper} used by most dialects. UUID encoding follows {@link UuidStorage}.
 */
public record DefaultJdbcValueMapper(UuidStorage uuidStorage) implements JdbcValueMapper {

    @Override
    public Object toJdbcValue(EntityField field, Object value) {
        return toJdbcValue(field, value, true);
    }

    Object toJdbcValue(EntityField field, Object value, boolean convertAttribute) {
        if (convertAttribute) {
            value = field.convertToDatabaseColumn(value);
        }
        if (value == null) {
            return null;
        }
        if (field.jdbcJavaType() == UUID.class) {
            UUID uuid = coerce(value, UUID.class, field);
            return switch (uuidStorage) {
                case NATIVE -> uuid;
                case BINARY -> UuidUtil.uuid2binary(uuid);
                case MICROSOFT_GUID -> UuidUtil.uuid2MicrosoftGuidBinary(uuid);
                case STRING -> uuid.toString();
            };
        }
        return value;
    }

    @Override
    public Object fromJdbcValue(EntityField field, Object value) {
        return fromJdbcValue(field, value, true);
    }

    Object fromJdbcValue(EntityField field, Object value, boolean convertAttribute) {
        if (value == null) {
            return null;
        }
        Object dbValue;
        if (field.jdbcJavaType() == UUID.class && uuidStorage == UuidStorage.BINARY && value instanceof byte[] bytes) {
            dbValue = UuidUtil.binary2Uuid(bytes);
        } else if (field.jdbcJavaType() == UUID.class && uuidStorage == UuidStorage.MICROSOFT_GUID && value instanceof byte[] bytes) {
            dbValue = UuidUtil.microsoftGuidBinary2Uuid(bytes);
        } else if (isSqlTemporal(field.jdbcJavaType())) {
            dbValue = toSqlTemporal(value, field.jdbcJavaType(), field);
        } else {
            dbValue = coerce(value, field.jdbcJavaType(), field);
        }
        return convertAttribute ? field.convertToEntityAttribute(dbValue) : dbValue;
    }

    private static boolean isSqlTemporal(Class<?> type) {
        return type == java.sql.Date.class || type == Time.class || type == Timestamp.class;
    }

    private static Object toSqlTemporal(Object value, Class<?> targetType, EntityField field) {
        if (targetType.isInstance(value)) {
            return value;
        }
        Long millis = epochMillis(value);
        if (millis != null) {
            if (targetType == java.sql.Date.class) {
                return new java.sql.Date(millis);
            }
            if (targetType == Time.class) {
                return new Time(millis);
            }
            return new Timestamp(millis);
        }
        return coerce(value, targetType, field);
    }

    private static Long epochMillis(Object value) {
        if (value instanceof Date date) {
            return date.getTime();
        }
        if (value instanceof Calendar calendar) {
            return calendar.getTimeInMillis();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Timestamp.valueOf(localDateTime).getTime();
        }
        if (value instanceof LocalDate localDate) {
            return java.sql.Date.valueOf(localDate).getTime();
        }
        if (value instanceof LocalTime localTime) {
            return Time.valueOf(localTime).getTime();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toEpochMilli();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant().toEpochMilli();
        }
        if (value instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        if (value instanceof String text && !text.isBlank() && isDigits(text)) {
            return Long.parseLong(text);
        }
        return null;
    }

    private static boolean isDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (i == 0 && c == '-') {
                continue;
            }
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return !text.equals("-");
    }

    private static <T> T coerce(Object value, Class<T> targetType, EntityField field) {
        try {
            return TypeCoercionUtil.coerce(value, targetType);
        } catch (TypeCoercionException e) {
            throw new MicroOrmException("Cannot map column '" + field.columnName()
                    + "' to " + targetType.getName(), e);
        }
    }
}
