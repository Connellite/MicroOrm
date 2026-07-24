package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.mapping.EntityField;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.type.UuidStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MssqlSchemaManager extends AbstractSchemaManager {

    public MssqlSchemaManager(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected String baseTypeForJava(Class<?> t, int length) {
        if (t == long.class || t == Long.class) {
            return "BIGINT";
        }
        if (t == int.class || t == Integer.class || t == short.class || t == Short.class || t == byte.class || t == Byte.class) {
            return "INT";
        }
        if (t == boolean.class || t == Boolean.class) {
            return "BIT";
        }
        if (t == double.class || t == Double.class) {
            return "FLOAT";
        }
        if (t == float.class || t == Float.class) {
            return "REAL";
        }
        if (t == String.class) {
            return "NVARCHAR(" + (length > 0 ? length : 255) + ")";
        }
        if (t == UUID.class) {
            UuidStorage storage = dialect.valueMapper().uuidStorage();
            if (storage == UuidStorage.MICROSOFT_GUID || storage == UuidStorage.NATIVE) {
                return "UNIQUEIDENTIFIER";
            }
            if (storage == UuidStorage.STRING) {
                return "NVARCHAR(36)";
            }
            return "BINARY(16)";
        }
        throw new IllegalArgumentException("Unsupported field type for MSSQL DDL: " + t.getName());
    }

    @Override
    protected String autoIncrementPrimaryKeyDefinition(EntityField field) {
        return baseType(field) + " IDENTITY(1,1) PRIMARY KEY";
    }

    @Override
    protected List<String> commentDdl(EntityModel model) {
        List<String> ddl = new ArrayList<>();
        String schema = model.schemaName() == null ? "dbo" : model.schemaName();
        if (!model.comment().isBlank()) {
            ddl.add(extendedPropertyDdl(model.comment(), schema, model.tableName(), null));
        }
        for (EntityField field : model.fields()) {
            if (!field.comment().isBlank()) {
                ddl.add(extendedPropertyDdl(field.comment(), schema, model.tableName(), field.columnName()));
            }
        }
        return ddl;
    }

    private String extendedPropertyDdl(String value, String schema, String table, String column) {
        String exists = column == null
                ? "SELECT 1 FROM sys.extended_properties ep "
                + "JOIN sys.tables t ON ep.major_id = t.object_id "
                + "JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "WHERE ep.name = N'MS_Description' AND ep.minor_id = 0 "
                + "AND s.name = N" + sqlStringLiteral(schema) + " AND t.name = N" + sqlStringLiteral(table)
                : "SELECT 1 FROM sys.extended_properties ep "
                + "JOIN sys.tables t ON ep.major_id = t.object_id "
                + "JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "JOIN sys.columns c ON ep.major_id = c.object_id AND ep.minor_id = c.column_id "
                + "WHERE ep.name = N'MS_Description' "
                + "AND s.name = N" + sqlStringLiteral(schema) + " AND t.name = N" + sqlStringLiteral(table)
                + " AND c.name = N" + sqlStringLiteral(column);
        String procedure = "IF EXISTS (" + exists + ") EXEC sys.sp_updateextendedproperty "
                + extendedPropertyArgs(value, schema, table, column)
                + " ELSE EXEC sys.sp_addextendedproperty "
                + extendedPropertyArgs(value, schema, table, column);
        return procedure;
    }

    private String extendedPropertyArgs(String value, String schema, String table, String column) {
        String args = "@name=N'MS_Description', @value=N" + sqlStringLiteral(value)
                + ", @level0type=N'SCHEMA', @level0name=N" + sqlStringLiteral(schema)
                + ", @level1type=N'TABLE', @level1name=N" + sqlStringLiteral(table);
        if (column != null) {
            args += ", @level2type=N'COLUMN', @level2name=N" + sqlStringLiteral(column);
        }
        return args;
    }
}
