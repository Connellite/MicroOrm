package io.github.connellite.microorm.mapping;

/**
 * Maps logical table/column names from annotations and Java field names to physical database identifiers
 * (for example snake_case) before SQL is generated.
 */
public interface PhysicalNamingStrategy {

    /** Physical table name for {@link io.github.connellite.microorm.annotation.Table#name()} or the class simple name. */
    String toPhysicalTableName(String logicalName);

    /** Physical column name for a field or explicit {@link io.github.connellite.microorm.annotation.Column#name()}. */
    String toPhysicalColumnName(String logicalName);
}
