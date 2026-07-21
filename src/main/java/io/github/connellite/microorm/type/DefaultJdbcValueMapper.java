package io.github.connellite.microorm.type;

import io.github.connellite.exception.TypeCoercionException;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.util.TypeCoercionUtil;
import io.github.connellite.util.UuidUtil;

import java.util.UUID;

/**
 * Default {@link JdbcValueMapper} used by most dialects. UUID encoding follows {@link UuidStorage}.
 */
public final class DefaultJdbcValueMapper implements JdbcValueMapper {

    private final UuidStorage uuidStorage;

    /** Creates a mapper with the given UUID JDBC representation. */
    public DefaultJdbcValueMapper(UuidStorage uuidStorage) {
        this.uuidStorage = uuidStorage;
    }

    @Override
    public UuidStorage uuidStorage() {
        return uuidStorage;
    }

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
        } else {
            dbValue = coerce(value, field.jdbcJavaType(), field);
        }
        return convertAttribute ? field.convertToEntityAttribute(dbValue) : dbValue;
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
