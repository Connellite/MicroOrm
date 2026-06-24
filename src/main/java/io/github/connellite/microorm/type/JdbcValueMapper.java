package io.github.connellite.microorm.type;

import io.github.connellite.microorm.mapping.EntityField;

public interface JdbcValueMapper {

    Object toJdbcValue(EntityField field, Object value);

    Object fromJdbcValue(EntityField field, Object value);
}
