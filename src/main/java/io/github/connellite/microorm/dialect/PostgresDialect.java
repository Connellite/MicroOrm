package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.dynamic.DefaultDynamicSqlGenerator;
import io.github.connellite.microorm.dynamic.DynamicSqlGenerator;
import io.github.connellite.microorm.dynamic.schema.DynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.PostgresDynamicSchemaManager;
import io.github.connellite.microorm.generation.SequenceTarget;
import io.github.connellite.microorm.schema.PostgresSchemaManager;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.sql.PostgresSqlGenerator;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;

/** PostgreSQL — unquoted identifiers folded to lower case; UUID stored natively. */
public final class PostgresDialect extends AbstractDialect {

    private final JdbcValueMapper valueMapper = new DefaultJdbcValueMapper(UuidStorage.NATIVE);

    private PostgresDialect() {
    }

    public static PostgresDialect getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final PostgresDialect INSTANCE = new PostgresDialect();
    }

    @Override
    protected String quotePreserveCase(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    protected String unquotedCatalogName(String identifier) {
        return lower(identifier);
    }

    @Override
    protected SqlGenerator createSqlGenerator(Dialect owner) {
        return new PostgresSqlGenerator(owner);
    }

    @Override
    protected SchemaManager createSchemaManager(Dialect owner) {
        return new PostgresSchemaManager(owner);
    }

    @Override
    protected DynamicSqlGenerator createDynamicSqlGenerator(Dialect owner) {
        return new DefaultDynamicSqlGenerator(owner);
    }

    @Override
    protected DynamicSchemaManager createDynamicSchemaManager(Dialect owner) {
        return new PostgresDynamicSchemaManager(owner);
    }

    @Override
    public JdbcValueMapper valueMapper() {
        return valueMapper;
    }

    @Override
    public boolean supportsSequences() {
        return true;
    }

    @Override
    public String createSequenceDdl(SequenceTarget target) {
        return "CREATE SEQUENCE IF NOT EXISTS " + sequenceSqlName(target)
                + " START WITH " + target.generation().initialValue()
                + " INCREMENT BY " + target.generation().allocationSize();
    }

    @Override
    public String nextSequenceValueSql(SequenceTarget target) {
        return "SELECT nextval('" + sequenceLiteralName(target) + "')";
    }
}
