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
        if (value == null) {
            return null;
        }
        if (field.javaType() == UUID.class) {
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
        if (value == null) {
            return null;
        }
        if (field.javaType() == UUID.class && uuidStorage == UuidStorage.BINARY && value instanceof byte[] bytes) {
            return UuidUtil.binary2Uuid(bytes);
        }
        if (field.javaType() == UUID.class && uuidStorage == UuidStorage.MICROSOFT_GUID && value instanceof byte[] bytes) {
            return UuidUtil.microsoftGuidBinary2Uuid(bytes);
        }
        return coerce(value, field.javaType(), field);
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
