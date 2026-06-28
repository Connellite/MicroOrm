package io.github.connellite.microorm.util;

import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Debug logging for generated SQL. Formatting runs only when debug is enabled.
 * <p>
 * Enable via SLF4J ({@code io.github.connellite.microorm.util.SqlDebugLog} at DEBUG)
 * or JUL ({@code FINE} on the same logger name).
 */
public final class SqlDebugLog {

    private static final Logger LOG = LoggerFactory.getLogger(SqlDebugLog.class);

    private SqlDebugLog() {
    }

    /** {@code true} when SQL debug logging is active. */
    public static boolean isEnabled() {
        return LOG.isDebugEnabled();
    }

    /** Logs a {@link BoundStatement} under the given operation label. */
    public static void boundStatement(String operation, BoundStatement statement) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug(() -> format(operation, statement.sql(), statement.parameters(), statement.collectionParameters()));
    }

    /** Logs a custom {@link Query} under the given operation label. */
    public static void query(String operation, Query query) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug(() -> format(operation, query.sql(), query.parameters(), query.collectionParameters()));
    }

    /** Logs raw SQL text (no parameters). */
    public static void sql(String operation, String sql) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug(() -> operation + ": " + sql);
    }

    /** Logs a JDBC batch statement and row count. */
    public static void batch(String operation, String sql, int rowCount) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug(() -> operation + " (" + rowCount + " rows): " + sql);
    }

    private static String format(
            String operation,
            String sql,
            Map<String, Object> parameters,
            Map<String, Collection<?>> collectionParameters) {
        Map<String, Object> merged = new LinkedHashMap<>(parameters);
        merged.putAll(collectionParameters);
        if (merged.isEmpty()) {
            return operation + ": " + sql;
        }
        return operation + ": " + sql + " | params=" + merged;
    }
}
