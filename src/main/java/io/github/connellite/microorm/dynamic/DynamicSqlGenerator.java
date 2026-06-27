package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.sql.BoundStatement;

import java.util.Map;

/**
 * Dialect-specific SQL builder for runtime-defined tables. Values are bound via named parameters — never concatenated.
 */
public interface DynamicSqlGenerator {

    /** Builds an INSERT for the given column values. Omits auto-increment PK when its value is null. */
    BoundStatement insert(DynamicTable table, Map<String, ?> values);

    /** Builds an UPDATE with {@code SET} and {@code WHERE} equality predicates. */
    BoundStatement update(DynamicTable table, Map<String, ?> setValues, Map<String, ?> whereValues);

    /** Builds a DELETE with equality predicates on {@code whereValues}. */
    BoundStatement delete(DynamicTable table, Map<String, ?> whereValues);

    /** Builds {@code SELECT col1, col2, ... FROM table}. */
    BoundStatement selectAll(DynamicTable table);

    /** Builds {@code SELECT ... WHERE col = :col AND ...}. Empty filters delegate to {@link #selectAll}. */
    BoundStatement selectWhere(DynamicTable table, Map<String, ?> filters);

    /** Builds {@code SELECT 1 ... WHERE ...} limited to one row for existence checks. */
    BoundStatement exists(DynamicTable table, Map<String, ?> whereValues);
}
