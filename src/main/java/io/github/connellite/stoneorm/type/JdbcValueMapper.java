package io.github.connellite.stoneorm.type;

import io.github.connellite.stoneorm.mapping.EntityField;

public interface JdbcValueMapper {

    Object toJdbcValue(EntityField field, Object value);

    Object fromJdbcValue(EntityField field, Object value);
}
