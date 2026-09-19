package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;

/** Shared identifier rendering: quoted identifiers preserve case; unquoted use dialect defaults. */
public abstract class AbstractDialect implements Dialect {

    private volatile SqlGenerator sqlGenerator;
    private volatile SchemaManager schemaManager;

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

    @Override
    public final SqlGenerator sqlGenerator() {
        return sqlGenerator(this);
    }

    @Override
    public final SqlGenerator sqlGenerator(Dialect owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner != this) {
            return createSqlGenerator(owner);
        }
        SqlGenerator cached = sqlGenerator;
        if (cached == null) {
            cached = createSqlGenerator(this);
            sqlGenerator = cached;
        }
        return cached;
    }

    @Override
    public final SchemaManager schemaManager() {
        return schemaManager(this);
    }

    @Override
    public final SchemaManager schemaManager(Dialect owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner != this) {
            return createSchemaManager(owner);
        }
        SchemaManager cached = schemaManager;
        if (cached == null) {
            cached = createSchemaManager(this);
            schemaManager = cached;
        }
        return cached;
    }

    @Override
    public final void createTable(Connection c, EntityModel model) throws SQLException {
        schemaManager(this).createTable(c, model);
    }

    @Override
    public final void syncTable(Connection c, EntityModel model) throws SQLException {
        schemaManager(this).syncTable(c, model);
    }

    @Override
    public final void dropTable(Connection c, EntityModel model) throws SQLException {
        schemaManager(this).dropTable(c, model);
    }

    protected abstract String quotePreserveCase(String identifier);

    protected abstract SqlGenerator createSqlGenerator(Dialect owner);

    protected abstract SchemaManager createSchemaManager(Dialect owner);

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
