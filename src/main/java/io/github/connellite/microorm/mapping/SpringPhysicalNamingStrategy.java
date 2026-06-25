package io.github.connellite.microorm.mapping;

import io.github.connellite.util.StringUtils;

/** Spring Boot-style physical naming: camelCase and PascalCase to snake_case. */
public final class SpringPhysicalNamingStrategy implements PhysicalNamingStrategy {

    public static final SpringPhysicalNamingStrategy INSTANCE = new SpringPhysicalNamingStrategy();

    private SpringPhysicalNamingStrategy() {
    }

    @Override
    public String toPhysicalTableName(String logicalName) {
        return StringUtils.toSnakeCase(logicalName);
    }

    @Override
    public String toPhysicalColumnName(String logicalName) {
        return StringUtils.toSnakeCase(logicalName);
    }
}
