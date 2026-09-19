package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaTemporalTypeTest {

    @Test
    void sqliteStoresTemporalTypesAsText() {
        SqliteSchemaManager schema = (SqliteSchemaManager) SqliteDialect.getInstance().schemaManager();
        assertEquals("TEXT", schema.baseTypeForJava(Date.class, 0));
        assertEquals("TEXT", schema.baseTypeForJava(Time.class, 0));
        assertEquals("TEXT", schema.baseTypeForJava(Timestamp.class, 0));
    }

    @Test
    void postgresUsesSqlTemporalTypes() {
        PostgresSchemaManager schema = (PostgresSchemaManager) PostgresDialect.getInstance().schemaManager();
        assertEquals("DATE", schema.baseTypeForJava(Date.class, 0));
        assertEquals("TIME", schema.baseTypeForJava(Time.class, 0));
        assertEquals("TIMESTAMP", schema.baseTypeForJava(Timestamp.class, 0));
    }

    @Test
    void mysqlUsesDatetimeForTimestamp() {
        MysqlSchemaManager schema = (MysqlSchemaManager) MysqlDialect.getInstance().schemaManager();
        assertEquals("DATE", schema.baseTypeForJava(Date.class, 0));
        assertEquals("TIME", schema.baseTypeForJava(Time.class, 0));
        assertEquals("DATETIME", schema.baseTypeForJava(Timestamp.class, 0));
    }

    @Test
    void mssqlUsesDatetime2ForTimestamp() {
        MssqlSchemaManager schema = (MssqlSchemaManager) MssqlDialect.getInstance().schemaManager();
        assertEquals("DATE", schema.baseTypeForJava(Date.class, 0));
        assertEquals("TIME", schema.baseTypeForJava(Time.class, 0));
        assertEquals("DATETIME2", schema.baseTypeForJava(Timestamp.class, 0));
    }

    @Test
    void oracleMapsTimeToTimestamp() {
        OracleSchemaManager schema = (OracleSchemaManager) OracleDialect.getInstance().schemaManager();
        assertEquals("DATE", schema.baseTypeForJava(Date.class, 0));
        assertEquals("TIMESTAMP", schema.baseTypeForJava(Time.class, 0));
        assertEquals("TIMESTAMP", schema.baseTypeForJava(Timestamp.class, 0));
    }
}
