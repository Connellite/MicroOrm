package io.github.connellite.microorm.mapping;

import java.util.Locale;

/** Preserves legacy MicroOrm naming: lowercase table names, column names as declared in Java. */
public final class IdentityPhysicalNamingStrategy implements PhysicalNamingStrategy {

    public static final IdentityPhysicalNamingStrategy INSTANCE = new IdentityPhysicalNamingStrategy();

    private IdentityPhysicalNamingStrategy() {
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
