package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.ManyToOneField;

import java.util.Map;

public interface SqlGenerator {

    BoundStatement insert(EntityModel model, Object entity);

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

    BoundStatement selectByJoinColumn(EntityModel model, String joinColumn, Object joinValue);

    static void validateColumnNames(EntityModel model) {
        validateIdentifier(model.tableName(), "table");
        for (EntityField f : model.fields()) {
            validateIdentifier(f.columnName(), "column / parameter");
        }
        for (ManyToOneField relation : model.manyToOneRelations()) {
            validateIdentifier(relation.joinColumn(), "column / parameter");
        }
    }

    static void validateIdentifier(String name, String kind) {
        if (name == null || name.isBlank()) {
            throw new MicroOrmException("Invalid SQL " + kind + " name: blank");
        }
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new MicroOrmException("Invalid SQL " + kind + " name (use [a-zA-Z_][a-zA-Z0-9_]*): " + name);
        }
    }

    static void validateSqlType(String sqlType, String context) {
        if (!sqlType.matches("[A-Za-z0-9_(),. ]+")) {
            throw new MicroOrmException("Invalid @Column(sqlType) on " + context + ": " + sqlType);
        }
    }
}
