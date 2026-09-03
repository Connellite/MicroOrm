package io.github.connellite.microorm.dialect;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.GeneratedValue;
import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.SequenceGenerator;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceDialectTest {

    @Entity
    @Table(name = "orders")
    static class Order {
        @Id
        @SequenceGenerator(name = "order_seq", sequenceName = "orders_seq", allocationSize = 5, initialValue = 10)
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
        private Long id;
    }

    @Test
    void postgresSequenceSqlUsesConfiguredGenerator() {
        EntityModel model = model();
        Dialect dialect = PostgresDialect.getInstance();

        assertTrue(dialect.supportsSequences());
        assertEquals(
                "CREATE SEQUENCE IF NOT EXISTS orders_seq START WITH 10 INCREMENT BY 5",
                dialect.createSequenceDdl(model, model.primaryKey()));
        assertEquals("SELECT nextval('orders_seq')", dialect.nextSequenceValueSql(model, model.primaryKey()));
    }

    @Test
    void oracleSequenceSqlUsesConfiguredGenerator() {
        EntityModel model = model();
        Dialect dialect = OracleDialect.getInstance();

        assertTrue(dialect.supportsSequences());
        assertEquals(
                "DECLARE sequence_count NUMBER; BEGIN SELECT COUNT(*) INTO sequence_count FROM all_sequences WHERE "
                        + "sequence_owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND sequence_name = 'ORDERS_SEQ'; "
                        + "IF sequence_count = 0 THEN EXECUTE IMMEDIATE "
                        + "'CREATE SEQUENCE ORDERS_SEQ START WITH 10 INCREMENT BY 5'; END IF; END;",
                dialect.createSequenceDdl(model, model.primaryKey()));
        assertEquals("SELECT ORDERS_SEQ.NEXTVAL FROM dual", dialect.nextSequenceValueSql(model, model.primaryKey()));
    }

    @Test
    void mssqlSequenceSqlUsesConfiguredGenerator() {
        EntityModel model = model();
        Dialect dialect = MssqlDialect.getInstance();

        assertTrue(dialect.supportsSequences());
        assertEquals(
                "IF NOT EXISTS (SELECT 1 FROM sys.sequences WHERE name = N'orders_seq') "
                        + "CREATE SEQUENCE orders_seq AS BIGINT START WITH 10 INCREMENT BY 5",
                dialect.createSequenceDdl(model, model.primaryKey()));
        assertEquals("SELECT NEXT VALUE FOR orders_seq", dialect.nextSequenceValueSql(model, model.primaryKey()));
    }

    private static EntityModel model() {
        return new EntityModelRegistry().register(Order.class);
    }
}
