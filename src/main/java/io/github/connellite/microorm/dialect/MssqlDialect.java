package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.dynamic.DynamicSqlGenerator;
import io.github.connellite.microorm.dynamic.MssqlDynamicSqlGenerator;
import io.github.connellite.microorm.dynamic.schema.DynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.MssqlDynamicSchemaManager;
import io.github.connellite.microorm.generation.SequenceTarget;
import io.github.connellite.microorm.schema.MssqlSchemaManager;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.sql.MssqlSqlGenerator;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;

/** Microsoft SQL Server — bracket-quoted identifiers; UUID stored as binary. */
public final class MssqlDialect extends AbstractDialect {

    private final JdbcValueMapper valueMapper = new DefaultJdbcValueMapper(UuidStorage.BINARY);

    private MssqlDialect() {
    }

    public static MssqlDialect getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final MssqlDialect INSTANCE = new MssqlDialect();
    }

    @Override
    protected String quotePreserveCase(String identifier) {
        return "[" + identifier.replace("]", "]]") + "]";
    }

    @Override
    protected SqlGenerator createSqlGenerator(Dialect owner) {
        return new MssqlSqlGenerator(owner);
    }

    @Override
    protected SchemaManager createSchemaManager(Dialect owner) {
        return new MssqlSchemaManager(owner);
    }

    @Override
    protected DynamicSqlGenerator createDynamicSqlGenerator(Dialect owner) {
        return new MssqlDynamicSqlGenerator(owner);
    }

    @Override
    protected DynamicSchemaManager createDynamicSchemaManager(Dialect owner) {
        return new MssqlDynamicSchemaManager(owner);
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
        String sequenceName = sequenceSqlName(target);
        String configuredName = target.generation().sequenceName();
        String catalogName = catalogName(SqlIdentifier.parse(configuredName.isBlank()
                ? target.tableName() + "_" + target.primaryKeyName() + "_seq"
                : configuredName));
        return "IF NOT EXISTS (SELECT 1 FROM sys.sequences WHERE name = N'"
                + catalogName.replace("'", "''")
                + "') CREATE SEQUENCE " + sequenceName
                + " AS BIGINT START WITH " + target.generation().initialValue()
                + " INCREMENT BY " + target.generation().allocationSize();
    }

    @Override
    public String nextSequenceValueSql(SequenceTarget target) {
        return "SELECT NEXT VALUE FOR " + sequenceSqlName(target);
    }
}
