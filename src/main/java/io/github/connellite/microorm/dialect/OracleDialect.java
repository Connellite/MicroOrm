package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.generation.SequenceTarget;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.schema.OracleSchemaManager;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.sql.OracleSqlGenerator;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.OracleJdbcValueMapper;

import java.sql.Connection;
import java.sql.SQLException;

/** Oracle Database — unquoted identifiers upper-cased; booleans mapped to NUMBER(1). */
public final class OracleDialect extends AbstractDialect {

    private final JdbcValueMapper valueMapper = new OracleJdbcValueMapper();
    private final SqlGenerator sqlGenerator = new OracleSqlGenerator(this);
    private final SchemaManager schemaManager = new OracleSchemaManager(this);

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
    public SqlGenerator sqlGenerator() {
        return sqlGenerator;
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

    @Override
    public void createTable(Connection c, EntityModel model) throws SQLException {
        schemaManager.createTable(c, model);
    }

    @Override
    public void syncTable(Connection c, EntityModel model) throws SQLException {
        schemaManager.syncTable(c, model);
    }

    @Override
    public void dropTable(Connection c, EntityModel model) throws SQLException {
        schemaManager.dropTable(c, model);
    }
}
