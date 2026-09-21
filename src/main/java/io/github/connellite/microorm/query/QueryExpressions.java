package io.github.connellite.microorm.query;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Factories and helpers for {@link QueryExpression} / {@link FunctionPath}.
 */
public final class QueryExpressions {

    private QueryExpressions() {
    }

    /**
     * Builds a scalar database function call with a variable argument list.
     * Arguments may be {@link FieldPath}, {@link FunctionPath}, literals, or {@code null} (SQL {@code NULL}).
     */
    public static FunctionPath fn(String name, Object... args) {
        List<QueryExpression> arguments = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                arguments.add(argument(arg));
            }
        }
        return new FunctionPath(new QueryExpression.Function(name, arguments), false);
    }

    static QueryExpression argument(Object arg) {
        if (arg == null) {
            return QueryExpression.NullValue.INSTANCE;
        }
        QueryExpression expression = asExpression(arg);
        return expression == null ? new QueryExpression.Bound(arg) : expression;
    }

    public static QueryExpression asExpression(Object value) {
        if (value instanceof ExprPath path) {
            return path.expression();
        }
        if (value instanceof QueryExpression expression) {
            return expression;
        }
        return null;
    }

    public static boolean isExpression(Object value) {
        return asExpression(value) != null;
    }

    static void validateFunctionName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("function name cannot be blank");
        }
        for (String part : name.split("\\.", -1)) {
            SqlGenerator.validateIdentifier(part, "function");
        }
    }

    public static String qualifiedFunctionName(String name, Function<SqlIdentifier, String> sqlName) {
        Objects.requireNonNull(sqlName, "sqlName");
        validateFunctionName(name);
        StringBuilder sql = new StringBuilder();
        String[] parts = name.split("\\.", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sql.append('.');
            }
            sql.append(sqlName.apply(SqlIdentifier.unquoted(parts[i])));
        }
        return sql.toString();
    }

    public static MicroOrmException unsupportedNullIn(String describe) {
        return new MicroOrmException("IN criterion does not support null values for " + describe);
    }
}
