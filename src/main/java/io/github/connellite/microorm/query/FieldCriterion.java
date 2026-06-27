package io.github.connellite.microorm.query;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Predicate that compares one mapped entity field with a value, collection, or SQL {@code NULL}.
 */
public record FieldCriterion(
        String fieldName,
        CriterionKind kind,
        ComparisonOperator operator,
        Object value,
        List<?> values) implements Criterion {

    public FieldCriterion {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        Objects.requireNonNull(kind, "kind");
        values = values == null ? List.of() : List.copyOf(values);
    }

    static FieldCriterion comparison(String fieldName, ComparisonOperator operator, Object value) {
        Objects.requireNonNull(operator, "operator");
        return new FieldCriterion(fieldName, CriterionKind.COMPARISON, operator, value, List.of());
    }

    static FieldCriterion in(String fieldName, Collection<?> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("IN values cannot be empty");
        }
        return new FieldCriterion(fieldName, CriterionKind.IN, null, null, List.copyOf(values));
    }

    static FieldCriterion like(String fieldName, String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        return new FieldCriterion(fieldName, CriterionKind.LIKE, null, pattern, List.of());
    }

    static FieldCriterion isNull(String fieldName) {
        return new FieldCriterion(fieldName, CriterionKind.IS_NULL, null, null, List.of());
    }

    static FieldCriterion isNotNull(String fieldName) {
        return new FieldCriterion(fieldName, CriterionKind.IS_NOT_NULL, null, null, List.of());
    }
}
