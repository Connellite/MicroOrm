package io.github.connellite.microorm.mapping;

import io.github.connellite.util.StringUtils;

/**
 * Spring Boot-style physical naming: camelCase and PascalCase converted to {@code snake_case}.
 * <p>
 * Obtain via {@link io.github.connellite.microorm.MicroOrm#snakeCaseNamingRegistry()}.
 */
public final class SnakeCasePhysicalNamingStrategy implements PhysicalNamingStrategy {

    private SnakeCasePhysicalNamingStrategy() {
    }

    public static SnakeCasePhysicalNamingStrategy getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final SnakeCasePhysicalNamingStrategy INSTANCE = new SnakeCasePhysicalNamingStrategy();
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
