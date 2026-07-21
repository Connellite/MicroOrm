package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.connection.KeepOpenConnectionProvider;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.BoundStatement;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.microorm.type.JdbcValueMapper;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionValidationTest {

    @Entity
    @Table(name = "validation_items")
    static class Item {
        @Id
        private java.util.UUID id;

        public Item() {
        }
    }

    @Test
    void insertRowRejectsNullEntity() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(NullPointerException.class, () -> session.insertRow(null));
            }
        }
    }

    @Test
    void updateRowRejectsNullEntity() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(NullPointerException.class, () -> session.updateRow(null));
            }
        }
    }

    @Test
    void deleteRowRejectsNullEntity() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(NullPointerException.class, () -> session.deleteRow(null));
            }
        }
    }

    @Test
    void batchInsertRejectsNullEntity() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                java.util.ArrayList<Item> batch = new java.util.ArrayList<>();
                batch.add(new Item());
                batch.add(null);
                assertThrows(IllegalArgumentException.class, () -> session.insertRows(batch));
            }
        }
    }

    @Test
    void selectRowRejectsNullId() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(MicroOrmException.class, () -> session.selectRow(Item.class, null));
            }
        }
    }

    @Test
    void deleteByIdRejectsNullId() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(Item.class);
            try (Session session = orm.openSession()) {
                assertThrows(MicroOrmException.class, () -> session.deleteById(Item.class, null));
            }
        }
    }

    @Entity
    @Table(name = "pk_only")
    static class PkOnly {
        @Id
        private UUID id;

        public PkOnly() {
        }
    }

    @Test
    void updateRowRejectsEntityWithOnlyPrimaryKey() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(PkOnly.class);
            try (Session session = orm.openSession()) {
                session.createEntity(PkOnly.class);
                PkOnly row = new PkOnly();
                row.id = UUID.randomUUID();
                session.insertRow(row);
                assertThrows(MicroOrmException.class, () -> session.updateRow(row));
            }
        }
    }

    @Entity
    @Table(name = "custom_relation_customers")
    static class RelationCustomer {
        @Id
        private UUID id;
    }

    @Entity
    @Table(name = "custom_relation_orders")
    static class RelationOrder {
        @Id
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "customer_id")
        private LazyRef<RelationCustomer> customer;
    }

    @Test
    void relationPersistenceRejectsSqlGeneratorWithoutRelationCapability() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = new MicroOrm(
                    new SqliteDialectWithoutRelationSql(),
                    new KeepOpenConnectionProvider(c),
                    new EntityModelRegistry()).register(RelationCustomer.class, RelationOrder.class);
            try (Session session = orm.openSession()) {
                RelationOrder order = new RelationOrder();
                order.id = UUID.randomUUID();

                MicroOrmException ex = assertThrows(MicroOrmException.class, () -> session.insertRow(order));

                assertTrue(ex.getMessage().contains("does not support relation persistence"));
            }
        }
    }

    @Entity
    @Table(name = "orphan_pk_owners")
    static class OrphanOwner {
        @Id
        private long id;

        @io.github.connellite.microorm.annotation.OneToMany(mappedBy = "owner")
        private io.github.connellite.microorm.relation.LazyCollection<OrphanChild> children;
    }

    @Entity
    @Table(name = "orphan_pk_children")
    static class OrphanChild {
        @Id
        private int id;

        @ManyToOne
        @JoinColumn(name = "owner_id")
        private LazyRef<OrphanOwner> owner;
    }

    @Test
    void deleteOrphanChildrenNormalizesNumericPrimaryKeysBeforeComparison() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            MicroOrm orm = MicroOrm.sqlite(c).register(OrphanOwner.class, OrphanChild.class);
            try (Session session = orm.openSession()) {
                session.createEntity(OrphanOwner.class);
                session.createEntity(OrphanChild.class);
                session.execute(Query.of(
                        "INSERT INTO orphan_pk_owners (id) VALUES (:id)").set("id", 1L));
                session.execute(Query.of(
                        "INSERT INTO orphan_pk_children (id, owner_id) VALUES (:id, :ownerId)")
                        .set("id", 5)
                        .set("ownerId", 1L));

                EntityModel ownerModel = orm.registry().get(OrphanOwner.class);
                EntityModel childModel = orm.registry().get(OrphanChild.class);
                session.deleteOrphanChildren(
                        ownerModel.oneToManyRelations().get(0),
                        1L,
                        Set.of(5L),
                        childModel);

                assertTrue(session.existsById(OrphanChild.class, 5));
            }
        }
    }

    private static final class SqliteDialectWithoutRelationSql implements Dialect {
        private final Dialect delegate = SqliteDialect.getInstance();
        private final SqlGenerator sqlGenerator = new DelegatingSqlGenerator(delegate.sqlGenerator());

        @Override
        public String sqlName(SqlIdentifier identifier) {
            return delegate.sqlName(identifier);
        }

        @Override
        public String catalogName(SqlIdentifier identifier) {
            return delegate.catalogName(identifier);
        }

        @Override
        public String jdbcColumnLabel(SqlIdentifier identifier) {
            return delegate.jdbcColumnLabel(identifier);
        }

        @Override
        public SqlGenerator sqlGenerator() {
            return sqlGenerator;
        }

        @Override
        public JdbcValueMapper valueMapper() {
            return delegate.valueMapper();
        }

        @Override
        public void createTable(Connection c, EntityModel model) throws SQLException {
            delegate.createTable(c, model);
        }

        @Override
        public void syncTable(Connection c, EntityModel model) throws SQLException {
            delegate.syncTable(c, model);
        }

        @Override
        public void dropTable(Connection c, EntityModel model) throws SQLException {
            delegate.dropTable(c, model);
        }
    }

    private record DelegatingSqlGenerator(SqlGenerator delegate) implements SqlGenerator {
        @Override
        public BoundStatement insert(EntityModel model, Object entity) {
            return delegate.insert(model, entity);
        }

        @Override
        public BoundStatement insert(EntityModel model, Object entity, boolean omitPk) {
            return delegate.insert(model, entity, omitPk);
        }

        @Override
        public String insertSql(EntityModel model, boolean omitPk) {
            return delegate.insertSql(model, omitPk);
        }

        @Override
        public Map<String, Object> insertParameters(EntityModel model, Object entity, boolean omitPk) {
            return delegate.insertParameters(model, entity, omitPk);
        }

        @Override
        public BoundStatement update(EntityModel model, Object entity) {
            return delegate.update(model, entity);
        }

        @Override
        public BoundStatement delete(EntityModel model, Object entity) {
            return delegate.delete(model, entity);
        }

        @Override
        public BoundStatement deleteById(EntityModel model, Object id) {
            return delegate.deleteById(model, id);
        }

        @Override
        public BoundStatement selectById(EntityModel model, Object id) {
            return delegate.selectById(model, id);
        }

        @Override
        public BoundStatement existsById(EntityModel model, Object id) {
            return delegate.existsById(model, id);
        }

        @Override
        public BoundStatement selectAll(EntityModel model) {
            return delegate.selectAll(model);
        }

        @Override
        public BoundStatement selectWhere(EntityModel model, Map<String, ?> filters) {
            return delegate.selectWhere(model, filters);
        }

        @Override
        public BoundStatement select(EntityModel model, EntityQuery<?> query) {
            return delegate.select(model, query);
        }

        @Override
        public BoundStatement select(EntityModel model, EntityQuery<?> query, EntityModelRegistry registry) {
            return delegate.select(model, query, registry);
        }

        @Override
        public BoundStatement selectByJoinColumn(EntityModel model, String joinColumn, Object joinValue) {
            return delegate.selectByJoinColumn(model, joinColumn, joinValue);
        }
    }
}
