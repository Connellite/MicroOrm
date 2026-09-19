package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.dynamic.DynamicSqlGenerator;
import io.github.connellite.microorm.dynamic.OracleDynamicSqlGenerator;
import io.github.connellite.microorm.dynamic.schema.DynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.OracleDynamicSchemaManager;
import io.github.connellite.microorm.generation.SequenceTarget;
import io.github.connellite.microorm.schema.OracleSchemaManager;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.sql.OracleSqlGenerator;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.OracleJdbcValueMapper;

/** Oracle Database — unquoted identifiers upper-cased; booleans mapped to NUMBER(1). */
public final class OracleDialect extends AbstractDialect {

    private final JdbcValueMapper valueMapper = new OracleJdbcValueMapper();

    private OracleDialect() {
    }

    public static OracleDialect getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final OracleDialect INSTANCE = new OracleDialect();
    }

    @Override
    protected String quotePreserveCase(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    protected String unquotedCatalogName(String identifier) {
        return upper(identifier);
    }

    @Override
    protected SqlGenerator createSqlGenerator(Dialect owner) {
        return new OracleSqlGenerator(owner);
    }

    @Override
    protected SchemaManager createSchemaManager(Dialect owner) {
        return new OracleSchemaManager(owner);
    }

    @Override
    protected DynamicSqlGenerator createDynamicSqlGenerator(Dialect owner) {
        return new OracleDynamicSqlGenerator(owner);
    }

    @Override
    protected DynamicSchemaManager createDynamicSchemaManager(Dialect owner) {
        return new OracleDynamicSchemaManager(owner);
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
        String configuredName = target.generation().sequenceName();
        String sequenceName = configuredName.isBlank()
                ? target.tableName() + "_" + target.primaryKeyName() + "_seq"
                : configuredName;
        String catalogSequenceName = catalogName(SqlIdentifier.parse(sequenceName));
        String ownerPredicate = target.schemaIdentifier() == null
                ? "sequence_owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')"
                : "sequence_owner = '" + catalogName(target.schemaIdentifier()).replace("'", "''") + "'";
        String createSequence = "CREATE SEQUENCE " + sequenceSqlName(target)
                + " START WITH " + target.generation().initialValue()
                + " INCREMENT BY " + target.generation().allocationSize();
        return "DECLARE sequence_count NUMBER; BEGIN SELECT COUNT(*) INTO sequence_count FROM all_sequences WHERE "
                + ownerPredicate + " AND sequence_name = '" + catalogSequenceName.replace("'", "''") + "'; "
                + "IF sequence_count = 0 THEN EXECUTE IMMEDIATE '" + createSequence.replace("'", "''")
                + "'; END IF; END;";
    }

    @Override
    public String nextSequenceValueSql(SequenceTarget target) {
        return "SELECT " + sequenceSqlName(target) + ".NEXTVAL FROM dual";
    }
}
