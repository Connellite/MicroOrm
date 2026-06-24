package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;

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

    static void validateColumnNames(EntityModel model) {
        for (EntityField f : model.fields()) {
            String n = f.columnName();
            if (!n.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new MicroOrmException("Invalid SQL column / parameter name (use [a-zA-Z_][a-zA-Z0-9_]*): " + n);
            }
        }
    }
}
