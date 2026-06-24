package io.github.connellite.stoneorm.jdbc;

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

    private static String productName(Connection connection) {
        try {
            String product = connection.getMetaData().getDatabaseProductName();
            return product == null ? null : product.toLowerCase(Locale.ROOT);
        } catch (SQLException e) {
            return null;
        }
    }
}
