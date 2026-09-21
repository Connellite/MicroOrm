package io.github.connellite.microorm.query;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Predicate that compares one mapped entity field with a value, collection, or SQL {@code NULL}.
 *
 * @param fieldName mapped Java field name or physical column name
 * @param kind predicate shape rendered to SQL
 * @param operator comparison operator for {@link CriterionKind#COMPARISON}; {@code null} otherwise
 * @param value scalar or pattern value; {@code null} for {@code IS NULL}, {@code IS NOT NULL}, {@code IN}, and ranges
 * @param values collection values for {@link CriterionKind#IN}, {@link CriterionKind#NOT_IN}, or range bounds; empty otherwise
 * @param ignoreCase whether to wrap compared SQL expressions in {@code LOWER(...)}
 */
public record FieldCriterion(
        String fieldName,
        CriterionKind kind,
        ComparisonOperator operator,
        Object value,
        List<?> values,
        boolean ignoreCase) implements Criterion {

    public FieldCriterion {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        Objects.requireNonNull(kind, "kind");
        values = values == null ? List.of() : List.copyOf(values);
    }

    static FieldCriterion comparison(String fieldName, ComparisonOperator operator, Object value, boolean ignoreCase) {
        Objects.requireNonNull(operator, "operator");
        return new FieldCriterion(fieldName, CriterionKind.COMPARISON, operator, value, List.of(), ignoreCase);
    }

    static FieldCriterion in(String fieldName, Collection<?> values, boolean ignoreCase) {
        return collection(fieldName, CriterionKind.IN, values, ignoreCase);
    }

    static FieldCriterion notIn(String fieldName, Collection<?> values, boolean ignoreCase) {
        return collection(fieldName, CriterionKind.NOT_IN, values, ignoreCase);
    }

    private static FieldCriterion collection(
            String fieldName,
            CriterionKind kind,
            Collection<?> values,
            boolean ignoreCase) {
        Objects.requireNonNull(values, "values");
        return new FieldCriterion(fieldName, kind, null, null, List.copyOf(values), ignoreCase);
    }

    static FieldCriterion like(String fieldName, String pattern, boolean ignoreCase) {
        Objects.requireNonNull(pattern, "pattern");
        return new FieldCriterion(fieldName, CriterionKind.LIKE, null, pattern, List.of(), ignoreCase);
    }

    static FieldCriterion notLike(String fieldName, String pattern, boolean ignoreCase) {
        Objects.requireNonNull(pattern, "pattern");
        return new FieldCriterion(fieldName, CriterionKind.NOT_LIKE, null, pattern, List.of(), ignoreCase);
    }

    static FieldCriterion between(String fieldName, Object lower, Object upper, boolean ignoreCase) {
        return between(fieldName, CriterionKind.BETWEEN, lower, upper, ignoreCase);
    }

    static FieldCriterion notBetween(String fieldName, Object lower, Object upper, boolean ignoreCase) {
        return between(fieldName, CriterionKind.NOT_BETWEEN, lower, upper, ignoreCase);
    }

    private static FieldCriterion between(
            String fieldName,
            CriterionKind kind,
            Object lower,
            Object upper,
            boolean ignoreCase) {
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(upper, "upper");
        return new FieldCriterion(fieldName, kind, null, null, List.of(lower, upper), ignoreCase);
    }

    static FieldCriterion isNull(String fieldName) {
        return new FieldCriterion(fieldName, CriterionKind.IS_NULL, null, null, List.of(), false);
    }

    static FieldCriterion isNotNull(String fieldName) {
        return new FieldCriterion(fieldName, CriterionKind.IS_NOT_NULL, null, null, List.of(), false);
    }
}
