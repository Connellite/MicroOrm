package io.github.connellite.microorm.query;

import java.util.Objects;

/**
 * Scalar database function used to build criteria and sort orders, for example
 * {@code EntitySelect.fn("dbo.obj2uuid", EntitySelect.field("objectId"))}.
 */
public record FunctionPath(QueryExpression.Function function, boolean ignoreCase) implements ExprPath {

    public FunctionPath {
        Objects.requireNonNull(function, "function");
    }

    @Override
    public QueryExpression expression() {
        return function;
    }

    /**
     * Wraps this function call in SQL {@code LOWER(...)} for subsequent predicates and sort orders.
     */
    @Override
    public FunctionPath lower() {
        return ignoreCase ? this : new FunctionPath(function, true);
    }
}
