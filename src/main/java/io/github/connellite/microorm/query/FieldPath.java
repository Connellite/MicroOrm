package io.github.connellite.microorm.query;

/**
 * Reference to a mapped entity field used to build criteria and sort orders.
 * <p>
 * The name may be either the Java field name or the physical column name. Joined fields use
 * {@code relation.field} and require a matching {@link EntitySelect#join(String)} declaration.
 * The path is resolved against the registered entity model when the query is executed.
 * {@link #lower()} wraps compared SQL expressions in {@code LOWER(...)} for case-insensitive matching.
 *
 * @param name Java field name, mapped column name, or joined path
 * @param ignoreCase whether predicates and sort orders wrap expressions in {@code LOWER(...)}
 */
public record FieldPath(String name, boolean ignoreCase) implements ExprPath {

    public FieldPath {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name cannot be blank");
        }
    }

    /** Creates a case-sensitive field path. */
    public FieldPath(String name) {
        this(name, false);
    }

    @Override
    public QueryExpression expression() {
        return new QueryExpression.Field(name);
    }

    /**
     * Wraps this field in SQL {@code LOWER(...)} for subsequent predicates and sort orders.
     * Bound values are also wrapped so {@code lower().eq("Ada")} matches {@code ada}.
     */
    @Override
    public FieldPath lower() {
        return ignoreCase ? this : new FieldPath(name, true);
    }
}
