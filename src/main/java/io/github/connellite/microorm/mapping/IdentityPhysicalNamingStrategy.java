package io.github.connellite.microorm.mapping;

import java.util.Locale;

/**
 * Default MicroOrm naming: table names lowercased from the entity class simple name;
 * column names match Java field names (no snake_case conversion).
 * <p>
 * Used by {@link io.github.connellite.microorm.MicroOrm} factory methods unless a custom
 * {@link EntityModelRegistry} is supplied.
 */
public final class IdentityPhysicalNamingStrategy implements PhysicalNamingStrategy {

    private IdentityPhysicalNamingStrategy() {
    }

    public static IdentityPhysicalNamingStrategy getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final IdentityPhysicalNamingStrategy INSTANCE = new IdentityPhysicalNamingStrategy();
    }

    @Override
    public String toPhysicalTableName(String logicalName) {
        return logicalName.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toPhysicalColumnName(String logicalName) {
        return logicalName;
    }
}
