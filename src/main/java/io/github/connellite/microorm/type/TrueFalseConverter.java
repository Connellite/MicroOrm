package io.github.connellite.microorm.type;

/**
 * Stores a {@link Boolean} as {@code 'T'} or {@code 'F'}.
 * Same column encoding as Hibernate {@code org.hibernate.type.TrueFalseConverter}.
 * {@code null} stays {@code null}. Any other character is read back as {@code null}.
 */
public final class TrueFalseConverter implements AttributeConverter<Boolean, Character> {

    @Override
    public Character convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute ? 'T' : 'F';
    }

    @Override
    public Boolean convertToEntityAttribute(Character dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData) {
            case 'T' -> true;
            case 'F' -> false;
            default -> null;
        };
    }
}
