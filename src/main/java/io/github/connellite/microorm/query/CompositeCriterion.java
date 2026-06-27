package io.github.connellite.microorm.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Criterion that combines child expressions with SQL {@code AND} or {@code OR}. */
public record CompositeCriterion(CompositeOperator operator, List<Criterion> criteria) implements Criterion {

    public CompositeCriterion {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.size() < 2) {
            throw new IllegalArgumentException("Composite criterion requires at least two children");
        }
        criteria = List.copyOf(criteria);
    }

    static Criterion and(Criterion left, Criterion right) {
        return combine(CompositeOperator.AND, left, right);
    }

    static Criterion or(Criterion left, Criterion right) {
        return combine(CompositeOperator.OR, left, right);
    }

    private static Criterion combine(CompositeOperator operator, Criterion left, Criterion right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        List<Criterion> criteria = new ArrayList<>();
        append(criteria, operator, left);
        append(criteria, operator, right);
        return new CompositeCriterion(operator, criteria);
    }

    private static void append(List<Criterion> target, CompositeOperator operator, Criterion criterion) {
        if (criterion instanceof CompositeCriterion composite && composite.operator() == operator) {
            target.addAll(composite.criteria());
            return;
        }
        target.add(criterion);
    }
}
