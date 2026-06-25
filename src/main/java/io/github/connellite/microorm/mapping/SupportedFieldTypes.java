package io.github.connellite.microorm.mapping;

import io.github.connellite.reflection.ReflectionUtil;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entity field types that can be bound to a JDBC {@link java.sql.PreparedStatement}
 * and coerced via ExtraLib {@link io.github.connellite.util.TypeCoercionUtil}.
 */
public final class SupportedFieldTypes {

    private static final Set<Class<?>> SCALAR_TYPES = Set.of(
            Boolean.class,
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Character.class,
            String.class,
            BigDecimal.class,
            byte[].class,
            Blob.class,
            Clob.class,
            NClob.class,
            Date.class,
            Time.class,
            Timestamp.class,
            java.util.Date.class,
            LocalDate.class,
            LocalTime.class,
            LocalDateTime.class,
            Instant.class,
            OffsetDateTime.class,
            ZonedDateTime.class,
            UUID.class,
            InputStream.class,
            Reader.class,
            Array.class,
            Ref.class,
            RowId.class,
            URL.class,
            SQLXML.class);

    private SupportedFieldTypes() {
    }

    public static boolean isSupported(Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (type.isEnum()) {
            return true;
        }
        Class<?> normalized = ReflectionUtil.primitiveToWrapper(type);
        return SCALAR_TYPES.contains(normalized);
    }
}
