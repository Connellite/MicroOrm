package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.sql.SqlIdentifier;

import java.util.Locale;

/** Shared identifier rendering: quoted identifiers preserve case; unquoted use dialect defaults. */
public abstract class AbstractDialect implements Dialect {

    @Override
    public final String sqlName(SqlIdentifier identifier) {
        if (identifier.quoted()) {
            return quotePreserveCase(identifier.text());
        }
        return unquotedSqlName(identifier.text());
    }

    @Override
    public final String catalogName(SqlIdentifier identifier) {
        if (identifier.quoted()) {
            return identifier.text();
        }
        return unquotedCatalogName(identifier.text());
    }

    @Override
    public String jdbcColumnLabel(SqlIdentifier identifier) {
        return catalogName(identifier);
    }

    protected abstract String quotePreserveCase(String identifier);

    protected String unquotedSqlName(String identifier) {
        return unquotedCatalogName(identifier);
    }

    protected String unquotedCatalogName(String identifier) {
        return identifier;
    }

    protected static String upper(String identifier) {
        return identifier.toUpperCase(Locale.ROOT);
    }

    protected static String lower(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }
}
