package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.generation.SequenceTarget;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.schema.MssqlSchemaManager;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.sql.MssqlSqlGenerator;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.DefaultJdbcValueMapper;
import io.github.connellite.microorm.type.JdbcValueMapper;
import io.github.connellite.microorm.type.UuidStorage;

import java.sql.Connection;
import java.sql.SQLException;

/** Microsoft SQL Server — bracket-quoted identifiers; UUID stored as binary. */
public final class MssqlDialect extends AbstractDialect {

    private final JdbcValueMapper valueMapper = new DefaultJdbcValueMapper(UuidStorage.BINARY);
    private final SqlGenerator sqlGenerator = new MssqlSqlGenerator(this);
    private final SchemaManager schemaManager = new MssqlSchemaManager(this);

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
