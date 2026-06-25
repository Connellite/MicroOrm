package io.github.connellite.microorm.util;

import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Debug logging for generated SQL; message formatting runs only when debug is enabled. */
public final class SqlDebugLog {

    private static final Logger LOG = LoggerFactory.getLogger(SqlDebugLog.class);

    private SqlDebugLog() {
    }

    public static boolean isEnabled() {
        return LOG.isDebugEnabled() ;
    }

    public static void boundStatement(String operation, BoundStatement statement) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug(() -> format(operation, statement.sql(), statement.parameters(), Map.of()));
    }

    public static void query(String operation, Query query) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug(() -> format(operation, query.sql(), query.parameters(), query.collectionParameters()));
    }

    public static void sql(String operation, String sql) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug(() -> operation + ": " + sql);
    }

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
