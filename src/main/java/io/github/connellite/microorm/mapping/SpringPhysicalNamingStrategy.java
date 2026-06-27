package io.github.connellite.microorm.mapping;

import io.github.connellite.util.StringUtils;

/**
 * Spring Boot-style physical naming: camelCase and PascalCase converted to {@code snake_case}.
 * <p>
 * Obtain via {@link io.github.connellite.microorm.MicroOrm#springNamingRegistry()}.
 */
public final class SpringPhysicalNamingStrategy implements PhysicalNamingStrategy {

    private SpringPhysicalNamingStrategy() {
    }

    public static SpringPhysicalNamingStrategy getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final SpringPhysicalNamingStrategy INSTANCE = new SpringPhysicalNamingStrategy();
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
