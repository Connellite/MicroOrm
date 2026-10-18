package io.github.connellite.microorm.type;

/**
 * Stores a {@link Boolean} as {@code 'Y'} or {@code 'N'}.
 * Same column encoding as Hibernate {@code org.hibernate.type.YesNoConverter}.
 * {@code null} stays {@code null}. Any other character is read back as {@code null}.
 */
public final class YesNoConverter implements AttributeConverter<Boolean, Character> {

    @Override
    public Character convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute ? 'Y' : 'N';
    }

    @Override
    public Boolean convertToEntityAttribute(Character dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData) {
            case 'Y' -> true;
            case 'N' -> false;
            default -> null;
        };
    }
}
