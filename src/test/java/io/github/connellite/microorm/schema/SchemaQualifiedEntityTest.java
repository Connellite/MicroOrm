package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.sql.BoundStatement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaQualifiedEntityTest {

    @Entity
    @Table(name = "schema_ddl_items", schema = "app")
    static class SchemaItem {
        @Id
        private long id;

        @Column(indexed = true)
        private String name;
    }

    private final EntityModel model = new EntityModelRegistry().register(SchemaItem.class);
    private final EntityField nameField = model.fields().stream()
            .filter(field -> field.columnName().equals("name"))
            .findFirst()
            .orElseThrow();

    @ParameterizedTest
    @EnumSource(DialectCase.class)
    void createTableUsesSchemaQualifiedName(DialectCase dialectCase) {
        assertEquals(dialectCase.createTableDdl(), dialectCase.schemaManager().buildCreateTableDdl(model));
    }

    @ParameterizedTest
    @EnumSource(DialectCase.class)
    void dropTableUsesSchemaQualifiedName(DialectCase dialectCase) {
        assertEquals(dialectCase.dropTableDdl(), dialectCase.schemaManager().dropTableDdl(model));
    }

    @ParameterizedTest
    @EnumSource(DialectCase.class)
    void createIndexUsesSchemaQualifiedTableName(DialectCase dialectCase) {
        assertEquals(dialectCase.createIndexDdl(), dialectCase.schemaManager().createIndexDdl(model, nameField));
    }

    @ParameterizedTest
    @EnumSource(DialectCase.class)
    void selectSqlUsesSchemaQualifiedTableName(DialectCase dialectCase) {
        BoundStatement select = dialectCase.dialect().sqlGenerator().selectAll(model);
        assertEquals(dialectCase.selectSql(), select.sql());
    }

    @ParameterizedTest
    @EnumSource(DialectCase.class)
    void insertSqlUsesSchemaQualifiedTableName(DialectCase dialectCase) {
        assertEquals(dialectCase.insertSql(), dialectCase.dialect().sqlGenerator().insertSql(model, false));
    }

    private enum DialectCase {
        SQLITE(
                SqliteDialect.getInstance(),
                new SqliteSchemaManager(SqliteDialect.getInstance()),
                "CREATE TABLE IF NOT EXISTS app.schema_ddl_items (id INTEGER NOT NULL PRIMARY KEY, name TEXT)",
                "DROP TABLE IF EXISTS app.schema_ddl_items",
                "CREATE INDEX IF NOT EXISTS app.idx_schema_ddl_items_name ON schema_ddl_items (name)",
                "SELECT app.schema_ddl_items.id, app.schema_ddl_items.name FROM app.schema_ddl_items",
                "INSERT INTO app.schema_ddl_items (id, name) VALUES (:id, :name)"),
        POSTGRES(
                PostgresDialect.getInstance(),
                new PostgresSchemaManager(PostgresDialect.getInstance()),
                "CREATE TABLE app.schema_ddl_items (id BIGINT NOT NULL PRIMARY KEY, name TEXT)",
                "DROP TABLE IF EXISTS app.schema_ddl_items",
                "CREATE INDEX idx_schema_ddl_items_name ON app.schema_ddl_items (name)",
                "SELECT app.schema_ddl_items.id, app.schema_ddl_items.name FROM app.schema_ddl_items",
                "INSERT INTO app.schema_ddl_items (id, name) VALUES (:id, :name)"),
        MYSQL(
                MysqlDialect.getInstance(),
                new MysqlSchemaManager(MysqlDialect.getInstance()),
                "CREATE TABLE app.schema_ddl_items (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255))",
                "DROP TABLE IF EXISTS app.schema_ddl_items",
                "CREATE INDEX idx_schema_ddl_items_name ON app.schema_ddl_items (name)",
                "SELECT app.schema_ddl_items.id, app.schema_ddl_items.name FROM app.schema_ddl_items",
                "INSERT INTO app.schema_ddl_items (id, name) VALUES (:id, :name)"),
        MSSQL(
                MssqlDialect.getInstance(),
                new MssqlSchemaManager(MssqlDialect.getInstance()),
                "CREATE TABLE app.schema_ddl_items (id BIGINT NOT NULL PRIMARY KEY, name NVARCHAR(255))",
                "DROP TABLE app.schema_ddl_items",
                "CREATE INDEX idx_schema_ddl_items_name ON app.schema_ddl_items (name)",
                "SELECT app.schema_ddl_items.id, app.schema_ddl_items.name FROM app.schema_ddl_items",
                "INSERT INTO app.schema_ddl_items (id, name) VALUES (:id, :name)"),
        ORACLE(
                OracleDialect.getInstance(),
                new OracleSchemaManager(OracleDialect.getInstance()),
                "CREATE TABLE APP.SCHEMA_DDL_ITEMS (ID NUMBER(19) NOT NULL PRIMARY KEY, NAME VARCHAR2(255))",
                "DROP TABLE APP.SCHEMA_DDL_ITEMS",
                "CREATE INDEX IDX_SCHEMA_DDL_ITEMS_NAME ON APP.SCHEMA_DDL_ITEMS (NAME)",
                "SELECT APP.SCHEMA_DDL_ITEMS.ID, APP.SCHEMA_DDL_ITEMS.NAME FROM APP.SCHEMA_DDL_ITEMS",
                "INSERT INTO APP.SCHEMA_DDL_ITEMS (ID, NAME) VALUES (:id, :name)");

        private final Dialect dialect;
        private final AbstractSchemaManager schemaManager;
        private final String createTableDdl;
        private final String dropTableDdl;
        private final String createIndexDdl;
        private final String selectSql;
        private final String insertSql;

        DialectCase(
                Dialect dialect,
                AbstractSchemaManager schemaManager,
                String createTableDdl,
                String dropTableDdl,
                String createIndexDdl,
                String selectSql,
                String insertSql) {
            this.dialect = dialect;
            this.schemaManager = schemaManager;
            this.createTableDdl = createTableDdl;
            this.dropTableDdl = dropTableDdl;
            this.createIndexDdl = createIndexDdl;
            this.selectSql = selectSql;
            this.insertSql = insertSql;
        }

        Dialect dialect() {
            return dialect;
        }

        AbstractSchemaManager schemaManager() {
            return schemaManager;
        }

        String createTableDdl() {
            return createTableDdl;
        }

        String dropTableDdl() {
            return dropTableDdl;
        }

        String createIndexDdl() {
            return createIndexDdl;
        }

        String selectSql() {
            return selectSql;
        }

        String insertSql() {
            return insertSql;
        }
    }
}
