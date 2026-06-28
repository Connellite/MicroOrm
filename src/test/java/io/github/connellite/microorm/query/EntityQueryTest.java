package io.github.connellite.microorm.query;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.sql.BoundStatement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityQueryTest {

    @Entity(name = "entity_query_items")
    public static class Item {
        @Id
        private long id;

        private String name;

        private boolean enabled;

        private String description;
    }

    private final EntityModel model = new EntityModelRegistry().register(Item.class);

    @Test
    void rendersPredicatesOrderingLimitAndOffset() {
        EntityQuery<Item> query = EntityQuery.of(Item.class)
                .where(EntityQuery.field("name").like("A%").or(EntityQuery.field("description").isNull()))
                .and(EntityQuery.field("enabled").eq(true))
                .orderBy(EntityQuery.field("name").desc(), EntityQuery.field("id").asc())
                .limit(10)
                .offset(5);

        BoundStatement statement = SqliteDialect.getInstance().sqlGenerator().select(model, query);

        assertEquals(
                "SELECT entity_query_items.id, entity_query_items.name, entity_query_items.enabled, "
                        + "entity_query_items.description FROM entity_query_items "
                        + "WHERE ((name LIKE :p1 OR description IS NULL) AND enabled = :p2) "
                        + "ORDER BY name DESC, id ASC LIMIT 10 OFFSET 5",
                statement.sql());
        assertEquals("A%", statement.parameters().get("p1"));
        assertEquals(true, statement.parameters().get("p2"));
    }

    @Test
    void rendersInNotAndNullPredicates() {
        EntityQuery<Item> query = EntityQuery.of(Item.class)
                .where(EntityQuery.field("name").in(List.of("a", "b")).and(EntityQuery.field("description").ne(null)))
                .and(EntityQuery.field("enabled").eq(false).not());

        BoundStatement statement = SqliteDialect.getInstance().sqlGenerator().select(model, query);

        assertEquals(
                "SELECT entity_query_items.id, entity_query_items.name, entity_query_items.enabled, "
                        + "entity_query_items.description FROM entity_query_items "
                        + "WHERE (name IN (:p1) AND description IS NOT NULL AND NOT (enabled = :p2))",
                statement.sql());
        assertEquals(List.of("a", "b"), statement.collectionParameters().get("p1"));
        assertEquals(false, statement.parameters().get("p2"));
    }

    @Test
    void rendersMultipleCollectionParameters() {
        EntityQuery<Item> query = EntityQuery.of(Item.class)
                .where(EntityQuery.field("name").in(List.of("a", "b"))
                        .or(EntityQuery.field("description").in(List.of("first", "second"))))
                .and(EntityQuery.field("enabled").eq(true));

        BoundStatement statement = SqliteDialect.getInstance().sqlGenerator().select(model, query);

        assertEquals(
                "SELECT entity_query_items.id, entity_query_items.name, entity_query_items.enabled, "
                        + "entity_query_items.description FROM entity_query_items "
                        + "WHERE ((name IN (:p1) OR description IN (:p2)) AND enabled = :p3)",
                statement.sql());
        assertEquals(List.of("a", "b"), statement.collectionParameters().get("p1"));
        assertEquals(List.of("first", "second"), statement.collectionParameters().get("p2"));
        assertEquals(true, statement.parameters().get("p3"));
    }

    @Test
    void rejectsInvalidCriteria() {
        assertThrows(IllegalArgumentException.class, () -> EntityQuery.field("name").in(List.of()));
        assertThrows(MicroOrmException.class, () -> SqliteDialect.getInstance().sqlGenerator()
                .select(model, EntityQuery.of(Item.class).where(EntityQuery.field("missing").eq(1))));
        assertThrows(MicroOrmException.class, () -> SqliteDialect.getInstance().sqlGenerator()
                .select(model, EntityQuery.of(String.class)));
    }

    @Test
    void rendersDialectSpecificPagination() {
        EntityQuery<Item> limitOnly = EntityQuery.of(Item.class).limit(3);
        assertTrue(MssqlDialect.getInstance().sqlGenerator().select(model, limitOnly).sql()
                .startsWith("SELECT TOP 3 entity_query_items.id, entity_query_items.name, "
                        + "entity_query_items.enabled, entity_query_items.description FROM entity_query_items"));

        EntityQuery<Item> mssqlOffset = EntityQuery.of(Item.class).offset(2);
        assertTrue(MssqlDialect.getInstance().sqlGenerator().select(model, mssqlOffset).sql()
                .endsWith("ORDER BY (SELECT 1) OFFSET 2 ROWS"));

        EntityQuery<Item> oraclePaged = EntityQuery.of(Item.class)
                .orderBy(EntityQuery.field("id").asc())
                .limit(3)
                .offset(2);
        assertTrue(OracleDialect.getInstance().sqlGenerator().select(model, oraclePaged).sql()
                .endsWith("ORDER BY ID ASC OFFSET 2 ROWS FETCH NEXT 3 ROWS ONLY"));
    }
}
