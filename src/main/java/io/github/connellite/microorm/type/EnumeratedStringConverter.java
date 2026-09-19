package io.github.connellite.microorm.type;

import io.github.connellite.microorm.exception.MicroOrmException;

/**
 * Stores an enum as its {@link Enum#name()} (JPA {@code EnumType.STRING}).
 */
public final class EnumeratedStringConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    public EnumeratedStringConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, dbData);
        } catch (IllegalArgumentException e) {
            throw new MicroOrmException("Unknown name '" + dbData + "' for enum " + enumType.getName(), e);
        }
    }
}
