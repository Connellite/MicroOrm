package io.github.connellite.microorm.mapping;

/** Maps logical entity/column names to physical database identifiers. */
public interface PhysicalNamingStrategy {

    String toPhysicalTableName(String logicalName);

    String toPhysicalColumnName(String logicalName);
}
