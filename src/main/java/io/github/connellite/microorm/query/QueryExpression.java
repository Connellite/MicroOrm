package io.github.connellite.microorm.query;

import java.util.List;
import java.util.Objects;

/**
 * SQL value expression used in {@link EntitySelect} / {@link io.github.connellite.microorm.dynamic.DynamicSelect}
 * criteria and sort orders: a mapped field, a scalar database function, a bound literal, or SQL {@code NULL}.
 */
public sealed interface QueryExpression
        permits QueryExpression.Field, QueryExpression.Function, QueryExpression.Bound, QueryExpression.NullValue {

    /** Mapped Java field name, physical column name, or joined {@code relation.field} path. */
    record Field(String name) implements QueryExpression {
        public Field {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("field name cannot be blank");
            }
        }
    }

    /**
     * Scalar function call {@code name(arg, ...)}. {@code name} may be schema-qualified ({@code dbo.uuid2obj}).
     */
    record Function(String name, List<QueryExpression> arguments) implements QueryExpression {
        public Function {
            QueryExpressions.validateFunctionName(name);
            arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        }
    }

    /** Literal bound as a named JDBC parameter. */
    record Bound(Object value) implements QueryExpression {
        public Bound {
            Objects.requireNonNull(value, "value");
        }
    }

    /** SQL {@code NULL} in a function argument list. */
    record NullValue() implements QueryExpression {
        static final NullValue INSTANCE = new NullValue();
    }

    /** Human-readable label for error messages. */
    default String describe() {
        if (this instanceof Field field) {
            return field.name();
        }
        if (this instanceof Function function) {
            return function.name() + "()";
        }
        if (this instanceof Bound bound) {
            return String.valueOf(bound.value());
        }
        return "null";
    }
}
