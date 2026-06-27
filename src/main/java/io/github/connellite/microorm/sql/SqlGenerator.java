package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.query.EntityQuery;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Dialect-specific SQL builder for entity CRUD. Application code normally uses {@link io.github.connellite.microorm.session.Session}
 * rather than calling these methods directly.
 */
public interface SqlGenerator {

    /** Pattern for unquoted SQL identifiers ({@code [a-zA-Z_][a-zA-Z0-9_]*}). */
    Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    /** Pattern for explicit {@link io.github.connellite.microorm.annotation.Column#sqlType()} values. */
    Pattern SQL_TYPE_PATTERN = Pattern.compile("[A-Za-z0-9_(),. ]+");

    BoundStatement insert(EntityModel model, Object entity);

    /** Insert with optional omission of the primary key column (auto-increment). */
    BoundStatement insert(EntityModel model, Object entity, boolean omitPk);

    String insertSql(EntityModel model, boolean omitPk);

    Map<String, Object> insertParameters(EntityModel model, Object entity, boolean omitPk);

    BoundStatement update(EntityModel model, Object entity);

    BoundStatement delete(EntityModel model, Object entity);

    BoundStatement deleteById(EntityModel model, Object id);

    BoundStatement selectById(EntityModel model, Object id);

    BoundStatement existsById(EntityModel model, Object id);

    BoundStatement selectAll(EntityModel model);

    BoundStatement selectWhere(EntityModel model, Map<String, ?> filters);

    BoundStatement select(EntityModel model, EntityQuery<?> query);

    BoundStatement selectByJoinColumn(EntityModel model, String joinColumn, Object joinValue);

    /** Validates table and column names on a built {@link EntityModel}. */
    static void validateColumnNames(EntityModel model) {
        validateIdentifier(model.tableIdentifier().text(), "table");
        for (EntityField f : model.fields()) {
            validateIdentifier(f.columnIdentifier().text(), "column / parameter");
        }
        for (ManyToOneField relation : model.manyToOneRelations()) {
            validateIdentifier(relation.joinColumnIdentifier().text(), "column / parameter");
        }
    }

    /** Validates a single identifier token. */
    static void validateIdentifier(String name, String kind) {
        if (name == null || name.isBlank()) {
            throw new MicroOrmException("Invalid SQL " + kind + " name: blank");
        }
        if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new MicroOrmException("Invalid SQL " + kind + " name (use [a-zA-Z_][a-zA-Z0-9_]*): " + name);
        }
    }

    /** Validates an explicit {@link io.github.connellite.microorm.annotation.Column#sqlType()} declaration. */
    static void validateSqlType(String sqlType, String context) {
        if (!SQL_TYPE_PATTERN.matcher(sqlType).matches()) {
            throw new MicroOrmException("Invalid @Column(sqlType) on " + context + ": " + sqlType);
        }
    }
}
