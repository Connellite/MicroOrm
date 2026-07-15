package io.github.connellite.microorm.query;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.annotation.OneToMany;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;
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

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    static final class Item_ {
        static final EntityQuery.Attribute<Item, String> NAME = EntityQuery.attribute("name");
        static final EntityQuery.Attribute<Item, Boolean> ENABLED = EntityQuery.attribute("enabled");
    }

    @Entity(name = "schema_query_items", schema = "app")
    public static class SchemaItem {
        @Id
        private long id;

        private String name;
    }

    @Entity(name = "entity_query_customers")
    public static class Customer {
        @Id
        private long id;

        private String name;
    }

    @Entity(name = "entity_query_orders")
    public static class OrderEntity {
        @Id
        private long id;

        private String title;

        @ManyToOne
        @JoinColumn(name = "customer_id")
        private LazyRef<Customer> customer;

        @OneToMany(mappedBy = "order")
        private LazyCollection<Line> lines;
    }

    @Entity(name = "entity_query_lines")
    public static class Line {
        @Id
        private long id;

        private String sku;

        @ManyToOne
        @JoinColumn(name = "order_id", nullable = false)
        private LazyRef<OrderEntity> order;
    }

    @Entity(name = "item_audit")
    public static class ItemAudit {
        @Id
        private long id;

        private long itemId;

        private String kind;
    }

    @Entity(name = "item_blocks")
    public static class ItemBlock {
        @Id
        private long id;

        private long itemId;

        private String reason;
    }

    @Entity(name = "item_refs")
    public static class ItemRef {
        @Id
        private long id;

        private long itemId;

        private boolean active;
    }

    @Entity(name = "archived_items")
    public static class ArchivedItem {
        @Id
        private long id;

        private long itemId;

        private int year;
    }

    private final EntityModel model = new EntityModelRegistry().register(Item.class);
    private final EntityModel schemaModel = new EntityModelRegistry().register(SchemaItem.class);
    private final EntityModelRegistry registry = new EntityModelRegistry();
    private final EntityModel orderModel = registry.register(OrderEntity.class);
    private final List<DialectCase> dialectCases = List.of(
            new DialectCase("sqlite", SqliteDialect.getInstance(), "entity_query_items.id"),
            new DialectCase("postgres", PostgresDialect.getInstance(), "entity_query_items.id"),
            new DialectCase("mysql", MysqlDialect.getInstance(), "entity_query_items.id"),
            new DialectCase("mssql", MssqlDialect.getInstance(), "entity_query_items.id"),
            new DialectCase("oracle", OracleDialect.getInstance(), "ENTITY_QUERY_ITEMS.ID"));

    EntityQueryTest() {
        registry.register(Customer.class);
        registry.register(Line.class);
    }

    private record DialectCase(String name, Dialect dialect, String idColumn) {
    }

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
                        + "WHERE ((entity_query_items.name LIKE :p1 OR entity_query_items.description IS NULL) "
                        + "AND entity_query_items.enabled = :p2) "
                        + "ORDER BY entity_query_items.name DESC, entity_query_items.id ASC LIMIT 10 OFFSET 5",
                statement.sql());
        assertEquals("A%", statement.parameters().get("p1"));
        assertEquals(true, statement.parameters().get("p2"));
    }

    @Test
    void createsFieldsFromGetterReferencesAndMetamodelAttributes() {
        EntityQuery<Item> query = EntityQuery.of(Item.class)
                .where(EntityQuery.field(Item::getName).eq("Ada"))
                .and(EntityQuery.field(Item::isEnabled).eq(true))
                .orderBy(EntityQuery.field(Item_.NAME).asc(), EntityQuery.field(Item_.ENABLED).desc());

        BoundStatement statement = SqliteDialect.getInstance().sqlGenerator().select(model, query);

        assertEquals(
                "SELECT entity_query_items.id, entity_query_items.name, entity_query_items.enabled, "
                        + "entity_query_items.description FROM entity_query_items "
                        + "WHERE (entity_query_items.name = :p1 AND entity_query_items.enabled = :p2) "
                        + "ORDER BY entity_query_items.name ASC, entity_query_items.enabled DESC",
                statement.sql());
        assertEquals("Ada", statement.parameters().get("p1"));
        assertEquals(true, statement.parameters().get("p2"));
    }

    @Test
    void rendersSchemaQualifiedTableNames() {
        BoundStatement select = SqliteDialect.getInstance().sqlGenerator().selectAll(schemaModel);
        String insert = SqliteDialect.getInstance().sqlGenerator().insertSql(schemaModel, false);

        assertEquals(
                "SELECT app.schema_query_items.id, app.schema_query_items.name FROM app.schema_query_items",
                select.sql());
        assertEquals(
                "INSERT INTO app.schema_query_items (id, name) VALUES (:id, :name)",
                insert);
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
                        + "WHERE (entity_query_items.name IN (:p1) AND entity_query_items.description IS NOT NULL "
                        + "AND NOT (entity_query_items.enabled = :p2))",
                statement.sql());
        assertEquals(List.of("a", "b"), statement.collectionParameters().get("p1"));
        assertEquals(false, statement.parameters().get("p2"));
    }

    @Test
    void rendersAdditionalPredicateHelpers() {
        EntityQuery<Item> query = EntityQuery.of(Item.class)
                .where(EntityQuery.field("name").notIn(List.of("a", "b")))
                .and(EntityQuery.field("description").notLike("%deprecated%"))
                .and(EntityQuery.field("id").between(10, 20))
                .and(EntityQuery.field("name").notBetween("m", "z"));

        BoundStatement statement = SqliteDialect.getInstance().sqlGenerator().select(model, query);

        assertEquals(
                "SELECT entity_query_items.id, entity_query_items.name, entity_query_items.enabled, "
                        + "entity_query_items.description FROM entity_query_items "
                        + "WHERE (entity_query_items.name NOT IN (:p1) "
                        + "AND entity_query_items.description NOT LIKE :p2 "
                        + "AND entity_query_items.id BETWEEN :p3 AND :p4 "
                        + "AND entity_query_items.name NOT BETWEEN :p5 AND :p6)",
                statement.sql());
        assertEquals(List.of("a", "b"), statement.collectionParameters().get("p1"));
        assertEquals("%deprecated%", statement.parameters().get("p2"));
        assertEquals(10, statement.parameters().get("p3"));
        assertEquals(20, statement.parameters().get("p4"));
        assertEquals("m", statement.parameters().get("p5"));
        assertEquals("z", statement.parameters().get("p6"));
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
                        + "WHERE ((entity_query_items.name IN (:p1) OR entity_query_items.description IN (:p2)) "
                        + "AND entity_query_items.enabled = :p3)",
                statement.sql());
        assertEquals(List.of("a", "b"), statement.collectionParameters().get("p1"));
        assertEquals(List.of("first", "second"), statement.collectionParameters().get("p2"));
        assertEquals(true, statement.parameters().get("p3"));
    }

    @Test
    void rendersExistsNotExistsAnyAndAllSubqueries() {
        EntityQuery<Item> query = EntityQuery.of(Item.class)
                .where(EntityQuery.exists(EntityQuery.of(ItemAudit.class)
                        .where(EntityQuery.field("kind").eq("CREATE"))))
                .and(EntityQuery.notExists(EntityQuery.of(ItemBlock.class)
                        .where(EntityQuery.field("reason").in(List.of("spam", "abuse")))))
                .and(EntityQuery.field("id").eqAny(EntityQuery.of(ItemRef.class)
                        .select("itemId")
                        .where(EntityQuery.field("active").eq(true))))
                .and(EntityQuery.field("id").gtAll(EntityQuery.of(ArchivedItem.class)
                        .select("itemId")
                        .where(EntityQuery.field("year").lt(2020))));

        BoundStatement statement = SqliteDialect.getInstance().sqlGenerator().select(model, query);

        assertEquals(
                "SELECT entity_query_items.id, entity_query_items.name, entity_query_items.enabled, "
                        + "entity_query_items.description FROM entity_query_items "
                        + "WHERE (EXISTS (SELECT 1 FROM item_audit WHERE item_audit.kind = :p1) "
                        + "AND NOT EXISTS (SELECT 1 FROM item_blocks WHERE item_blocks.reason IN (:p2)) "
                        + "AND entity_query_items.id = ANY (SELECT item_refs.itemId FROM item_refs "
                        + "WHERE item_refs.active = :p3) "
                        + "AND entity_query_items.id > ALL (SELECT archived_items.itemId FROM archived_items "
                        + "WHERE archived_items.year < :p4))",
                statement.sql());
        assertEquals("CREATE", statement.parameters().get("p1"));
        assertEquals(true, statement.parameters().get("p3"));
        assertEquals(2020, statement.parameters().get("p4"));
        assertEquals(List.of("spam", "abuse"), statement.collectionParameters().get("p2"));
    }

    @Test
    void rendersRawSqlSubquery() {
        EntityQuery<Item> query = EntityQuery.of(Item.class)
                .where(EntityQuery.exists(Query.of(
                        "SELECT 1 FROM item_audit a WHERE a.item_id = entity_query_items.id AND a.kind = :auditKind")
                        .set("auditKind", "CREATE")))
                .and(EntityQuery.notExists(Query.of(
                        "SELECT 1 FROM item_blocks b WHERE b.item_id = entity_query_items.id AND b.reason IN (:reasons)")
                        .setCollection("reasons", List.of("spam", "abuse"))))
                .and(EntityQuery.field("id").eqAny(Query.of(
                        "SELECT r.item_id FROM item_refs r WHERE r.active = :refActive")
                        .set("refActive", true)))
                .and(EntityQuery.field("id").gtAll(Query.of(
                        "SELECT archived.item_id FROM archived_items archived WHERE archived.year < :archiveYear")
                        .set("archiveYear", 2020)));

        BoundStatement statement = SqliteDialect.getInstance().sqlGenerator().select(model, query);

        assertEquals(
                "SELECT entity_query_items.id, entity_query_items.name, entity_query_items.enabled, "
                        + "entity_query_items.description FROM entity_query_items "
                        + "WHERE (EXISTS (SELECT 1 FROM item_audit a "
                        + "WHERE a.item_id = entity_query_items.id AND a.kind = :auditKind) "
                        + "AND NOT EXISTS (SELECT 1 FROM item_blocks b "
                        + "WHERE b.item_id = entity_query_items.id AND b.reason IN (:reasons)) "
                        + "AND entity_query_items.id = ANY (SELECT r.item_id FROM item_refs r WHERE r.active = :refActive) "
                        + "AND entity_query_items.id > ALL (SELECT archived.item_id FROM archived_items archived "
                        + "WHERE archived.year < :archiveYear))",
                statement.sql());
        assertEquals("CREATE", statement.parameters().get("auditKind"));
        assertEquals(true, statement.parameters().get("refActive"));
        assertEquals(2020, statement.parameters().get("archiveYear"));
        assertEquals(List.of("spam", "abuse"), statement.collectionParameters().get("reasons"));

        for (DialectCase dialectCase : dialectCases) {
            BoundStatement dialectStatement = dialectCase.dialect().sqlGenerator().select(model, query);
            assertTrue(dialectStatement.sql().contains("EXISTS (SELECT 1 FROM item_audit a "
                    + "WHERE a.item_id = entity_query_items.id AND a.kind = :auditKind)"), dialectCase.name());
            assertTrue(dialectStatement.sql().contains("NOT EXISTS (SELECT 1 FROM item_blocks b "
                    + "WHERE b.item_id = entity_query_items.id AND b.reason IN (:reasons))"), dialectCase.name());
            assertTrue(dialectStatement.sql().contains(dialectCase.idColumn()
                    + " = ANY (SELECT r.item_id FROM item_refs r WHERE r.active = :refActive)"), dialectCase.name());
            assertTrue(dialectStatement.sql().contains(dialectCase.idColumn()
                    + " > ALL (SELECT archived.item_id FROM archived_items archived "
                    + "WHERE archived.year < :archiveYear)"), dialectCase.name());
            assertEquals("CREATE", dialectStatement.parameters().get("auditKind"), dialectCase.name());
            assertEquals(true, dialectStatement.parameters().get("refActive"), dialectCase.name());
            assertEquals(2020, dialectStatement.parameters().get("archiveYear"), dialectCase.name());
            assertEquals(List.of("spam", "abuse"), dialectStatement.collectionParameters().get("reasons"), dialectCase.name());
        }
    }

    @Test
    void rejectsSubqueryParameterNameConflicts() {
        EntityQuery<Item> query = EntityQuery.of(Item.class)
                .where(EntityQuery.field("name").eq("Ada"))
                .and(EntityQuery.exists(Query.of("SELECT 1 WHERE value = :p1").set("p1", 1)));

        assertThrows(MicroOrmException.class,
                () -> SqliteDialect.getInstance().sqlGenerator().select(model, query));
    }

    @Test
    void rendersManyToOneJoin() {
        EntityQuery<OrderEntity> query = EntityQuery.of(OrderEntity.class)
                .join("customer")
                .where(EntityQuery.field("customer.name").eq("Acme"))
                .orderBy(EntityQuery.field("customer.name").asc());

        BoundStatement statement = SqliteDialect.getInstance().sqlGenerator().select(orderModel, query, registry);

        assertEquals(
                "SELECT entity_query_orders.id, entity_query_orders.title, entity_query_orders.customer_id "
                        + "FROM entity_query_orders INNER JOIN entity_query_customers j1 "
                        + "ON entity_query_orders.customer_id = j1.id "
                        + "WHERE j1.name = :p1 ORDER BY j1.name ASC",
                statement.sql());
        assertEquals("Acme", statement.parameters().get("p1"));
    }

    @Test
    void rendersOneToManyJoinWithDistinctRootRows() {
        EntityQuery<OrderEntity> query = EntityQuery.of(OrderEntity.class)
                .leftJoin("lines")
                .where(EntityQuery.field("lines.sku").in(List.of("A", "B")));

        BoundStatement statement = SqliteDialect.getInstance().sqlGenerator().select(orderModel, query, registry);

        assertEquals(
                "SELECT DISTINCT entity_query_orders.id, entity_query_orders.title, entity_query_orders.customer_id "
                        + "FROM entity_query_orders LEFT JOIN entity_query_lines j1 "
                        + "ON entity_query_orders.id = j1.order_id WHERE j1.sku IN (:p1)",
                statement.sql());
        assertEquals(List.of("A", "B"), statement.collectionParameters().get("p1"));
    }

    @Test
    void rendersSupportedDialectJoinsAndRejectsUnsupportedJoins() {
        assertTrue(SqliteDialect.getInstance().sqlGenerator()
                .select(orderModel, EntityQuery.of(OrderEntity.class).crossJoin("customer"), registry)
                .sql()
                .contains("CROSS JOIN entity_query_customers j1"));

        assertThrows(MicroOrmException.class, () -> SqliteDialect.getInstance().sqlGenerator()
                .select(orderModel, EntityQuery.of(OrderEntity.class).rightJoin("customer"), registry));
        assertThrows(MicroOrmException.class, () -> SqliteDialect.getInstance().sqlGenerator()
                .select(orderModel, EntityQuery.of(OrderEntity.class).fullJoin("lines"), registry));
        assertThrows(MicroOrmException.class, () -> MysqlDialect.getInstance().sqlGenerator()
                .select(orderModel, EntityQuery.of(OrderEntity.class).fullJoin("lines"), registry));

        assertTrue(MysqlDialect.getInstance().sqlGenerator()
                .select(orderModel, EntityQuery.of(OrderEntity.class).rightJoin("customer"), registry)
                .sql()
                .contains("RIGHT JOIN entity_query_customers j1 ON entity_query_orders.customer_id = j1.id"));
    }

    @Test
    void rejectsInvalidCriteria() {
        assertThrows(IllegalArgumentException.class, () -> EntityQuery.field("name").in(List.of()));
        assertThrows(IllegalArgumentException.class, () -> EntityQuery.field("name").notIn(List.of()));
        assertThrows(NullPointerException.class, () -> EntityQuery.field("name").notLike(null));
        assertThrows(NullPointerException.class, () -> EntityQuery.field("id").between(null, 2));
        assertThrows(NullPointerException.class, () -> EntityQuery.field("id").notBetween(1, null));
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

        EntityQuery<Item> postgresOffset = EntityQuery.of(Item.class).offset(2);
        assertTrue(PostgresDialect.getInstance().sqlGenerator().select(model, postgresOffset).sql()
                .endsWith("OFFSET 2"));

        EntityQuery<Item> mysqlOffset = EntityQuery.of(Item.class).offset(2);
        assertTrue(MysqlDialect.getInstance().sqlGenerator().select(model, mysqlOffset).sql()
                .endsWith("LIMIT 18446744073709551615 OFFSET 2"));

        EntityQuery<Item> oraclePaged = EntityQuery.of(Item.class)
                .orderBy(EntityQuery.field("id").asc())
                .limit(3)
                .offset(2);
        assertTrue(OracleDialect.getInstance().sqlGenerator().select(model, oraclePaged).sql()
                .endsWith("ORDER BY ENTITY_QUERY_ITEMS.ID ASC OFFSET 2 ROWS FETCH NEXT 3 ROWS ONLY"));
    }
}
