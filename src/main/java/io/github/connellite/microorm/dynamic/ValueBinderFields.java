package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityField;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stub fields used to delegate JDBC conversion to {@link io.github.connellite.microorm.type.JdbcValueMapper}
 * without a mapped entity class.
 */
final class ValueBinderFields {

    String stringValue;
    Integer intValue;
    Long longValue;
    Boolean boolValue;
    UUID uuidValue;
    BigDecimal decimalValue;
    Double doubleValue;
    LocalDateTime dateTimeValue;
    LocalDate dateValue;

    private static final Map<LogicalType, EntityField> FIELDS = new EnumMap<>(LogicalType.class);

    static {
        register(LogicalType.STRING, "stringValue");
        register(LogicalType.TEXT, "stringValue");
        register(LogicalType.INT, "intValue");
        register(LogicalType.LONG, "longValue");
        register(LogicalType.BOOL, "boolValue");
        register(LogicalType.UUID, "uuidValue");
        register(LogicalType.DECIMAL, "decimalValue");
        register(LogicalType.DOUBLE, "doubleValue");
        register(LogicalType.DATETIME, "dateTimeValue");
        register(LogicalType.DATE, "dateValue");
    }

    private ValueBinderFields() {
    }

    static EntityField field(LogicalType type) {
        EntityField field = FIELDS.get(type);
        if (field == null) {
            throw new MicroOrmException("Unsupported logical type: " + type);
        }
        return field;
    }

    private static void register(LogicalType type, String fieldName) {
        try {
            Field javaField = ValueBinderFields.class.getDeclaredField(fieldName);
            FIELDS.put(type, new EntityField(javaField, "_stub", false, false, true));
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
