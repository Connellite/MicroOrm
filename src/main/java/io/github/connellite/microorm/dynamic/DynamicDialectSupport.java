package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dialect.MssqlDialect;
import io.github.connellite.microorm.dialect.MysqlDialect;
import io.github.connellite.microorm.dialect.OracleDialect;
import io.github.connellite.microorm.dialect.PostgresDialect;
import io.github.connellite.microorm.dialect.SqliteDialect;
import io.github.connellite.microorm.dynamic.schema.DynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.MssqlDynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.MysqlDynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.OracleDynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.PostgresDynamicSchemaManager;
import io.github.connellite.microorm.dynamic.schema.SqliteDynamicSchemaManager;
import io.github.connellite.microorm.exception.MicroOrmException;

/**
 * Resolves dialect-specific {@link DynamicSqlGenerator} and {@link DynamicSchemaManager} implementations
 * for a configured {@link Dialect}.
 */
public final class DynamicDialectSupport {

    private DynamicDialectSupport() {
    }

    /** Returns the SQL generator for the given dialect. */
    public static DynamicSqlGenerator sqlGenerator(Dialect dialect) {
        return new AbstractDynamicSqlGenerator(dialect) {
            @Override
            protected String limitOne(String sql) {
                if (dialect instanceof OracleDialect) {
                    return sql + " FETCH FIRST 1 ROWS ONLY";
                }
                if (dialect instanceof MssqlDialect) {
                    if (sql.startsWith("SELECT 1 FROM ")) {
                        return "SELECT TOP 1 1 FROM " + sql.substring("SELECT 1 FROM ".length());
                    }
                    return sql;
                }
                return sql + " LIMIT 1";
            }
        };
    }

    /** Returns the schema manager for the given dialect. */
    public static DynamicSchemaManager schemaManager(Dialect dialect) {
        if (dialect instanceof SqliteDialect) {
            return new SqliteDynamicSchemaManager(dialect);
        }
        if (dialect instanceof PostgresDialect) {
            return new PostgresDynamicSchemaManager(dialect);
        }
        if (dialect instanceof MysqlDialect) {
            return new MysqlDynamicSchemaManager(dialect);
        }
        if (dialect instanceof MssqlDialect) {
            return new MssqlDynamicSchemaManager(dialect);
        }
        if (dialect instanceof OracleDialect) {
            return new OracleDynamicSchemaManager(dialect);
        }
        throw new MicroOrmException("Unsupported dialect for dynamic tables: " + dialect.getClass().getName());
    }
}
