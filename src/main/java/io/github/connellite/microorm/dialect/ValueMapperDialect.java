package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.dynamic.DynamicSqlGenerator;
import io.github.connellite.microorm.dynamic.schema.DynamicSchemaManager;
import io.github.connellite.microorm.generation.SequenceTarget;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.schema.SchemaManager;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.JdbcValueMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Vendor dialect with a substituted {@link JdbcValueMapper}; SQL and DDL are rebound to this instance. */
final class ValueMapperDialect implements Dialect {

    private final Dialect delegate;
    private final JdbcValueMapper valueMapper;
    private final SqlGenerator sqlGenerator;
    private final SchemaManager schemaManager;
    private final DynamicSqlGenerator dynamicSqlGenerator;
    private final DynamicSchemaManager dynamicSchemaManager;

    ValueMapperDialect(Dialect delegate, JdbcValueMapper valueMapper) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.valueMapper = Objects.requireNonNull(valueMapper, "valueMapper");
        this.sqlGenerator = delegate.sqlGenerator(this);
        this.schemaManager = delegate.schemaManager(this);
        this.dynamicSqlGenerator = delegate.dynamicSqlGenerator(this);
        this.dynamicSchemaManager = delegate.dynamicSchemaManager(this);
    }

    @Override
    public String sqlName(SqlIdentifier identifier) {
        return delegate.sqlName(identifier);
    }

    @Override
    public String catalogName(SqlIdentifier identifier) {
        return delegate.catalogName(identifier);
    }

    @Override
    public String jdbcColumnLabel(SqlIdentifier identifier) {
        return delegate.jdbcColumnLabel(identifier);
    }

    @Override
    public SqlGenerator sqlGenerator() {
        return sqlGenerator;
    }

    @Override
    public SqlGenerator sqlGenerator(Dialect owner) {
        return delegate.sqlGenerator(owner);
    }

    @Override
    public SchemaManager schemaManager() {
        return schemaManager;
    }

    @Override
    public SchemaManager schemaManager(Dialect owner) {
        return owner == this ? schemaManager : delegate.schemaManager(owner);
    }

    @Override
    public DynamicSqlGenerator dynamicSqlGenerator() {
        return dynamicSqlGenerator;
    }

    @Override
    public DynamicSqlGenerator dynamicSqlGenerator(Dialect owner) {
        return delegate.dynamicSqlGenerator(owner);
    }

    @Override
    public DynamicSchemaManager dynamicSchemaManager() {
        return dynamicSchemaManager;
    }

    @Override
    public DynamicSchemaManager dynamicSchemaManager(Dialect owner) {
        return owner == this ? dynamicSchemaManager : delegate.dynamicSchemaManager(owner);
    }

    @Override
    public JdbcValueMapper valueMapper() {
        return valueMapper;
    }

    @Override
    public Dialect withValueMapper(JdbcValueMapper valueMapper) {
        Objects.requireNonNull(valueMapper, "valueMapper");
        if (this.valueMapper == valueMapper) {
            return this;
        }
        return new ValueMapperDialect(delegate, valueMapper);
    }

    @Override
    public Dialect unwrap() {
        return delegate.unwrap();
    }

    @Override
    public boolean supportsSequences() {
        return delegate.supportsSequences();
    }

    @Override
    public String createSequenceDdl(EntityModel model, EntityField pk) {
        return delegate.createSequenceDdl(model, pk);
    }

    @Override
    public String createSequenceDdl(SequenceTarget target) {
        return delegate.createSequenceDdl(target);
    }

    @Override
    public String nextSequenceValueSql(EntityModel model, EntityField pk) {
        return delegate.nextSequenceValueSql(model, pk);
    }

    @Override
    public String nextSequenceValueSql(SequenceTarget target) {
        return delegate.nextSequenceValueSql(target);
    }

    @Override
    public String sequenceSqlName(EntityModel model, EntityField pk) {
        return delegate.sequenceSqlName(model, pk);
    }

    @Override
    public String sequenceSqlName(SequenceTarget target) {
        return delegate.sequenceSqlName(target);
    }

    @Override
    public String sequenceLiteralName(EntityModel model, EntityField pk) {
        return delegate.sequenceLiteralName(model, pk);
    }

    @Override
    public String sequenceLiteralName(SequenceTarget target) {
        return delegate.sequenceLiteralName(target);
    }

    @Override
    public void createTable(Connection c, EntityModel model) throws SQLException {
        schemaManager(this).createTable(c, model);
    }

    @Override
    public void syncTable(Connection c, EntityModel model) throws SQLException {
        schemaManager(this).syncTable(c, model);
    }

    @Override
    public void dropTable(Connection c, EntityModel model) throws SQLException {
        schemaManager(this).dropTable(c, model);
    }
}
