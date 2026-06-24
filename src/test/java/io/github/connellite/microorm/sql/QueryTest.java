package io.github.connellite.microorm.sql;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryTest {

    @Test
    void ofRejectsBlankSql() {
        assertThrows(IllegalArgumentException.class, () -> Query.of("   "));
    }

    @Test
    void setBindsScalarParameters() {
        Query query = Query.of("SELECT 1 WHERE id = :id").set("id", 42);
        assertEquals(42, query.parameters().get("id"));
    }

    @Test
    void setAllBindsMultipleParameters() {
        Query query = Query.of("SELECT 1")
                .setAll(Map.of("a", 1, "b", "two"));
        assertEquals(2, query.parameters().size());
    }

    @Test
    void setCollectionRejectsEmptyValues() {
        Query query = Query.of("SELECT 1 WHERE id IN (:ids)");
        assertThrows(IllegalArgumentException.class, () -> query.setCollection("ids", List.of()));
    }

    @Test
    void rejectsDuplicateParameterNames() {
        Query query = Query.of("SELECT 1 WHERE a = :x AND b = :y").set("x", 1);
        assertThrows(IllegalArgumentException.class, () -> query.set("x", 2));
        assertThrows(IllegalArgumentException.class, () -> query.setCollection("x", List.of(1, 2)));
    }

    @Test
    void rejectsBlankParameterName() {
        Query query = Query.of("SELECT 1");
        assertThrows(IllegalArgumentException.class, () -> query.set(" ", 1));
    }
}
