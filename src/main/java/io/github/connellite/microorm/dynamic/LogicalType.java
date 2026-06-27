package io.github.connellite.microorm.dynamic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Logical column type for runtime-defined tables. Mapped to JDBC and SQL DDL per {@link io.github.connellite.microorm.dialect.Dialect}.
 */
public enum LogicalType {

    /** Variable-length text (dialect-specific VARCHAR/TEXT). */
    STRING(String.class),

    /** Long text (TEXT/CLOB/NTEXT where supported). */
    TEXT(String.class),

    /** 32-bit integer. */
    INT(Integer.class),

    /** 64-bit integer. */
    LONG(Long.class),

    /** Boolean flag. */
    BOOL(Boolean.class),

    /** Universally unique identifier. */
    UUID(UUID.class),

    /** Fixed-precision decimal. */
    DECIMAL(BigDecimal.class),

    /** Floating-point number. */
    DOUBLE(Double.class),

    /** Date and time without time zone. */
    DATETIME(LocalDateTime.class),

    /** Calendar date without time. */
    DATE(LocalDate.class);

    private final Class<?> javaType;

    LogicalType(Class<?> javaType) {
        this.javaType = javaType;
    }

    /** Preferred Java type for values in {@link java.util.Map}-based CRUD. */
    public Class<?> javaType() {
        return javaType;
    }
}
