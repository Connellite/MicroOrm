package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.schema.SqliteSchemaManager;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqliteSqlGenerator;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;

/** SQLite 3 — unquoted identifiers are case-insensitive; backticks request quoted SQL. */
public final class SqliteDialect extends AbstractDialect {

    private final JdbcValueMapper valueMapper = new DefaultJdbcValueMapper(UuidStorage.STRING);

    private SqliteDialect() {
    }

    public static SqliteDialect getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final SqliteDialect INSTANCE = new SqliteDialect();
    }

    @Override
    protected String quotePreserveCase(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    protected SqlGenerator createSqlGenerator(Dialect owner) {
        return new SqliteSqlGenerator(owner);
    }

    @Override
    protected SchemaManager createSchemaManager(Dialect owner) {
        return new SqliteSchemaManager(owner);
    }

    @Override
    public JdbcValueMapper valueMapper() {
        return valueMapper;
    }
}
