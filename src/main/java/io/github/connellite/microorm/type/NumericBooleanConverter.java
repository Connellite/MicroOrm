package io.github.connellite.microorm.type;

/**
 * Stores a {@link Boolean} as {@code 1} or {@code 0}.
 * Same column encoding as Hibernate {@code org.hibernate.type.NumericBooleanConverter}.
 * {@code null} stays {@code null}. Any other integer is read back as {@code null}.
 */
public final class NumericBooleanConverter implements AttributeConverter<Boolean, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute ? 1 : 0;
    }

    @Override
    public Boolean convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData) {
            case 1 -> true;
            case 0 -> false;
            default -> null;
        };
    }
}
