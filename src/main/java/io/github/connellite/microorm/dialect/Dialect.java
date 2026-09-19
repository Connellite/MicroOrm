package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.dynamic.DynamicSqlGenerator;
import io.github.connellite.microorm.dynamic.schema.DynamicSchemaManager;
import io.github.connellite.microorm.exception.MicroOrmException;
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

/**
 * Database-specific identifier quoting, DDL, DML SQL generation, and JDBC value mapping.
 * Obtain a singleton from {@link io.github.connellite.microorm.MicroOrm} factory methods
 * ({@code SqliteDialect.getInstance()}, etc.) or pass to {@link io.github.connellite.microorm.MicroOrm#MicroOrm}.
 */
public interface Dialect {

    /** Renders an identifier for SQL (DDL/DML), applying dialect quoting and default case rules. */
    String sqlName(SqlIdentifier identifier);

    /** Name as stored in the database catalog (metadata queries, schema sync). */
    String catalogName(SqlIdentifier identifier);

    /**
     * Column label for {@link java.sql.ResultSet#getObject(String)} when hydrating entities.
     * Defaults to {@link #catalogName(SqlIdentifier)}.
     */
    default String jdbcColumnLabel(SqlIdentifier identifier) {
        return catalogName(identifier);
    }

    /** Dialect-specific {@link SqlGenerator} bound to this instance. */
    SqlGenerator sqlGenerator();

    /**
     * SQL generator using this dialect's syntax, bound to {@code owner}.
     * Wrappers pass themselves so generated SQL uses the owner's {@link #valueMapper()}.
     */
    default SqlGenerator sqlGenerator(Dialect owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner == this) {
            return sqlGenerator();
        }
        throw unsupportedRebind("sqlGenerator");
    }

    /** Schema manager bound to this instance. */
    default SchemaManager schemaManager() {
        return schemaManager(this);
    }

    /**
     * Schema manager using this dialect's DDL, bound to {@code owner}.
     * Wrappers pass themselves so UUID column types follow the owner's {@link #valueMapper()}.
     */
    default SchemaManager schemaManager(Dialect owner) {
        Objects.requireNonNull(owner, "owner");
        throw unsupportedRebind("schemaManager");
    }

    /** Dynamic-table SQL generator bound to this instance. */
    default DynamicSqlGenerator dynamicSqlGenerator() {
        throw unsupportedDynamic();
    }

    /**
     * Dynamic-table SQL generator using this dialect's syntax, bound to {@code owner}.
     * Wrappers pass themselves so generated SQL uses the owner's {@link #valueMapper()}.
     */
    default DynamicSqlGenerator dynamicSqlGenerator(Dialect owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner == this) {
            return dynamicSqlGenerator();
        }
        throw unsupportedRebind("dynamicSqlGenerator");
    }

    /** Dynamic-table schema manager bound to this instance. */
    default DynamicSchemaManager dynamicSchemaManager() {
        return dynamicSchemaManager(this);
    }

    /**
     * Dynamic-table schema manager using this dialect's DDL, bound to {@code owner}.
     * Wrappers pass themselves so UUID column types follow the owner's {@link #valueMapper()}.
     */
    default DynamicSchemaManager dynamicSchemaManager(Dialect owner) {
        Objects.requireNonNull(owner, "owner");
        throw unsupportedDynamic();
    }

    /** Converts Java field values to JDBC parameters and back (UUID storage, booleans, etc.). */
    JdbcValueMapper valueMapper();

    /**
     * Returns a dialect that keeps this vendor's SQL/DDL syntax but uses {@code valueMapper}.
     * {@link #sqlGenerator()}, {@link #schemaManager()}, {@link #dynamicSqlGenerator()},
     * and {@link #dynamicSchemaManager()} are rebound to the returned instance.
     */
    default Dialect withValueMapper(JdbcValueMapper valueMapper) {
        Objects.requireNonNull(valueMapper, "valueMapper");
        if (valueMapper() == valueMapper) {
            return this;
        }
        return new ValueMapperDialect(this, valueMapper);
    }

    /**
     * Underlying vendor dialect when this instance is a {@link #withValueMapper(JdbcValueMapper) mapper wrapper};
     * otherwise {@code this}.
     */
    default Dialect unwrap() {
        return this;
    }

    /** Returns whether this dialect supports standalone sequence-backed primary keys. */
    default boolean supportsSequences() {
        return false;
    }

    /** DDL that creates the sequence used by a {@link io.github.connellite.microorm.annotation.GenerationType#SEQUENCE} id. */
    default String createSequenceDdl(EntityModel model, EntityField pk) {
        return createSequenceDdl(new SequenceTarget(model.schemaIdentifier(), model.tableIdentifier(), pk.columnIdentifier(), pk.idGeneration()));
    }

    /** DDL that creates the sequence used by a sequence-backed primary key. */
    default String createSequenceDdl(SequenceTarget target) {
        throw unsupportedSequences();
    }

    /** Query that returns the next sequence value for a {@link io.github.connellite.microorm.annotation.GenerationType#SEQUENCE} id. */
    default String nextSequenceValueSql(EntityModel model, EntityField pk) {
        return nextSequenceValueSql(new SequenceTarget(
                model.schemaIdentifier(),
                model.tableIdentifier(),
                pk.columnIdentifier(),
                pk.idGeneration()));
    }

    /** Query that returns the next value for a sequence-backed primary key. */
    default String nextSequenceValueSql(SequenceTarget target) {
        throw unsupportedSequences();
    }

    /** Renders the physical sequence name, using the entity schema when present. */
    default String sequenceSqlName(EntityModel model, EntityField pk) {
        return sequenceSqlName(new SequenceTarget(
                model.schemaIdentifier(),
                model.tableIdentifier(),
                pk.columnIdentifier(),
                pk.idGeneration()));
    }

    /** Renders the physical sequence name, using the target schema when present. */
    default String sequenceSqlName(SequenceTarget target) {
        String configuredName = target.generation().sequenceName();
        String sequenceName = configuredName.isBlank()
                ? target.tableName() + "_" + target.primaryKeyName() + "_seq"
                : configuredName;
        SqlIdentifier sequenceIdentifier = SqlIdentifier.parse(sequenceName);
        SqlGenerator.validateIdentifier(sequenceIdentifier.text(), "sequence");
        String rendered = sqlName(sequenceIdentifier);
        return target.schemaIdentifier() == null ? rendered : sqlName(target.schemaIdentifier()) + "." + rendered;
    }

    /** Sequence name for SQL string literals such as PostgreSQL {@code nextval('...')}. */
    default String sequenceLiteralName(EntityModel model, EntityField pk) {
        return sequenceLiteralName(new SequenceTarget(
                model.schemaIdentifier(),
                model.tableIdentifier(),
                pk.columnIdentifier(),
                pk.idGeneration()));
    }

    /** Sequence name for SQL string literals such as PostgreSQL {@code nextval('...')}. */
    default String sequenceLiteralName(SequenceTarget target) {
        return sequenceSqlName(target).replace("'", "''");
    }

    /** Creates the entity table and indexes when missing. */
    void createTable(Connection c, EntityModel model) throws SQLException;

    /** Adds missing nullable columns and indexes without dropping data. */
    void syncTable(Connection c, EntityModel model) throws SQLException;

    /** Drops the entity table (destructive). */
    void dropTable(Connection c, EntityModel model) throws SQLException;

    private MicroOrmException unsupportedSequences() {
        return new MicroOrmException("GenerationType.SEQUENCE is not supported by " + getClass().getSimpleName());
    }

    private MicroOrmException unsupportedDynamic() {
        return new MicroOrmException("Unsupported dialect for dynamic tables: " + getClass().getName());
    }

    private MicroOrmException unsupportedRebind(String method) {
        return new MicroOrmException(getClass().getSimpleName()
                + " must override " + method + "(Dialect) to support withValueMapper");
    }
}
