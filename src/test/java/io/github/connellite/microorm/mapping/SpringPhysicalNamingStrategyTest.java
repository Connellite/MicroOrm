package io.github.connellite.microorm.mapping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpringPhysicalNamingStrategyTest {

    private final SpringPhysicalNamingStrategy strategy = SpringPhysicalNamingStrategy.getInstance();

    @Test
    void convertsPascalCaseTableNamesToSnakeCase() {
        assertEquals("order_item", strategy.toPhysicalTableName("OrderItem"));
    }

    @Test
    void convertsCamelCaseColumnNamesToSnakeCase() {
        assertEquals("first_name", strategy.toPhysicalColumnName("firstName"));
    }

    @Test
    void preservesExistingSnakeCaseNames() {
        assertEquals("registry_items", strategy.toPhysicalTableName("registry_items"));
        assertEquals("document_id", strategy.toPhysicalColumnName("document_id"));
    }
}
