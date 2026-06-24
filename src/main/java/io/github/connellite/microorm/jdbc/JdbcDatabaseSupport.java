package io.github.connellite.microorm.jdbc;

import io.github.connellite.microorm.mapping.EntityModel;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

final class JdbcDatabaseSupport {

    private JdbcDatabaseSupport() {
    }

    static boolean isSqlite(Connection connection) {
        String product = productName(connection);
        return product != null && product.contains("sqlite");
    }

    static boolean supportsBatchGeneratedKeys(Connection connection) {
        String product = productName(connection);
        if (product == null) {
            return true;
        }
        return !product.contains("microsoft sql server") && !product.contains("oracle");
    }

    /**
     * Oracle JDBC returns ROWID from {@code getGeneratedKeys()} unless the PK column name is specified.
     */
    static boolean requiresGeneratedKeyColumnNames(Connection connection) {
        String product = productName(connection);
        return product != null && product.contains("oracle");
    }

    /**
     * Oracle uppercases unquoted identifiers; this ORM creates quoted, case-sensitive column names.
     */
    static String[] oracleGeneratedKeyColumnNames(EntityModel model) {
        String col = model.primaryKey().columnName();
        return new String[] {"\"" + col.replace("\"", "\"\"") + "\""};
    }

    private static String productName(Connection connection) {
        try {
            String product = connection.getMetaData().getDatabaseProductName();
            return product == null ? null : product.toLowerCase(Locale.ROOT);
        } catch (SQLException e) {
            return null;
        }
    }
}
