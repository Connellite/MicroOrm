package io.github.connellite.microorm.type;

import io.github.connellite.microorm.exception.MicroOrmException;

/**
 * Stores an enum as its {@link Enum#ordinal()} (JPA {@code EnumType.ORDINAL}).
 */
public final class EnumeratedOrdinalConverter<E extends Enum<E>> implements AttributeConverter<E, Integer> {

    private final Class<E> enumType;

    public EnumeratedOrdinalConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public Integer convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.ordinal();
    }

    @Override
    public E convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        E[] constants = enumType.getEnumConstants();
        int ordinal = dbData;
        if (ordinal < 0 || ordinal >= constants.length) {
            throw new MicroOrmException("Unknown ordinal " + ordinal + " for enum " + enumType.getName());
        }
        return constants[ordinal];
    }
}
