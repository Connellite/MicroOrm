package io.github.connellite.stoneorm.type;

import io.github.connellite.exception.TypeCoercionException;
import io.github.connellite.stoneorm.StoneOrmException;
import io.github.connellite.stoneorm.mapping.EntityField;
import io.github.connellite.util.TypeCoercionUtil;
import io.github.connellite.util.UuidUtil;

import java.util.UUID;

public final class DefaultJdbcValueMapper implements JdbcValueMapper {

    private final UuidStorage uuidStorage;

    public DefaultJdbcValueMapper(UuidStorage uuidStorage) {
        this.uuidStorage = uuidStorage;
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
        return coerce(value, field.javaType(), field);
    }

    private static <T> T coerce(Object value, Class<T> targetType, EntityField field) {
        try {
            return TypeCoercionUtil.coerce(value, targetType);
        } catch (TypeCoercionException e) {
            throw new StoneOrmException("Cannot map column '" + field.columnName()
                    + "' to " + targetType.getName(), e);
        }
    }
}
