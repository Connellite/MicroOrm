package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.schema.MysqlSchemaManager;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.sql.MysqlSqlGenerator;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;

/** MySQL / MariaDB — unquoted identifiers lower-cased; UUID stored as binary. */
public final class MysqlDialect extends AbstractDialect {

    private final JdbcValueMapper valueMapper = new DefaultJdbcValueMapper(UuidStorage.BINARY);

    private MysqlDialect() {
    }

    public static MysqlDialect getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final MysqlDialect INSTANCE = new MysqlDialect();
    }

    @Override
    protected String quotePreserveCase(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    protected String unquotedCatalogName(String identifier) {
        return lower(identifier);
    }

    @Override
    protected SqlGenerator createSqlGenerator(Dialect owner) {
        return new MysqlSqlGenerator(owner);
    }

    @Override
    protected SchemaManager createSchemaManager(Dialect owner) {
        return new MysqlSchemaManager(owner);
    }

    @Override
    public JdbcValueMapper valueMapper() {
        return valueMapper;
    }
}
