package io.github.connellite.microorm.type;

/**
 * Converts an entity attribute to the value stored in a database column and back.
 *
 * @param <X> entity attribute type
 * @param <Y> database column Java type
 */
public interface AttributeConverter<X, Y> {

    /** Converts an entity attribute value to a database column value. */
    Y convertToDatabaseColumn(X attribute);

    /** Converts a database column value to an entity attribute value. */
    X convertToEntityAttribute(Y dbData);
}
