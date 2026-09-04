package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.annotation.CascadeType;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.sql.SqlIdentifier;
import io.github.connellite.reflection.MethodHandleReflectionUtil;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Set;

/** Metadata for a {@link io.github.connellite.microorm.annotation.ManyToMany} collection wrapper field. */
public final class ManyToManyField implements CollectionRelation {

    private final Field javaField;
    private final VarHandle varHandle;
    private final Class<?> targetEntityClass;
    private final String mappedBy;
    private final SqlIdentifier joinTableIdentifier;
    private final SqlIdentifier joinTableSchemaIdentifier;
    private final SqlIdentifier ownerJoinColumnIdentifier;
    private final SqlIdentifier targetJoinColumnIdentifier;
    private final Class<?> ownerForeignKeyJavaType;
    private final Class<?> targetForeignKeyJavaType;
    private final Set<CascadeType> cascade;

    public ManyToManyField(
            Field javaField,
            Class<?> targetEntityClass,
            String mappedBy,
            SqlIdentifier joinTableIdentifier,
            SqlIdentifier joinTableSchemaIdentifier,
            SqlIdentifier ownerJoinColumnIdentifier,
            SqlIdentifier targetJoinColumnIdentifier,
            Class<?> ownerForeignKeyJavaType,
            Class<?> targetForeignKeyJavaType,
            CascadeType[] cascade) {
        this.javaField = javaField;
        this.targetEntityClass = targetEntityClass;
        this.mappedBy = mappedBy == null ? "" : mappedBy;
        this.joinTableIdentifier = joinTableIdentifier;
        this.joinTableSchemaIdentifier = joinTableSchemaIdentifier;
        this.ownerJoinColumnIdentifier = ownerJoinColumnIdentifier;
        this.targetJoinColumnIdentifier = targetJoinColumnIdentifier;
        this.ownerForeignKeyJavaType = ownerForeignKeyJavaType;
        this.targetForeignKeyJavaType = targetForeignKeyJavaType;
        this.cascade = Cascades.copyCascade(cascade);
        try {
            this.varHandle = MethodHandleReflectionUtil.varHandle(javaField);
        } catch (IllegalAccessException e) {
            throw new MicroOrmException("Cannot access field " + javaField.getName(), e);
        }
    }

    @Override
    public Field javaField() {
        return javaField;
    }

    @Override
    public VarHandle varHandle() {
        return varHandle;
    }

    @Override
    public Class<?> targetEntityClass() {
        return targetEntityClass;
    }

    public String mappedBy() {
        return mappedBy;
    }

    public boolean owning() {
        return mappedBy.isBlank();
    }

    public SqlIdentifier joinTableIdentifier() {
        requireOwning("joinTableIdentifier");
        return joinTableIdentifier;
    }

    public SqlIdentifier joinTableSchemaIdentifier() {
        requireOwning("joinTableSchemaIdentifier");
        return joinTableSchemaIdentifier;
    }

    public SqlIdentifier ownerJoinColumnIdentifier() {
        requireOwning("ownerJoinColumnIdentifier");
        return ownerJoinColumnIdentifier;
    }

    public SqlIdentifier targetJoinColumnIdentifier() {
        requireOwning("targetJoinColumnIdentifier");
        return targetJoinColumnIdentifier;
    }

    public String joinTable() {
        return joinTableIdentifier().text();
    }

    public String ownerJoinColumn() {
        return ownerJoinColumnIdentifier().text();
    }

    public String targetJoinColumn() {
        return targetJoinColumnIdentifier().text();
    }

    public Class<?> ownerForeignKeyJavaType() {
        requireOwning("ownerForeignKeyJavaType");
        return ownerForeignKeyJavaType;
    }

    public Class<?> targetForeignKeyJavaType() {
        requireOwning("targetForeignKeyJavaType");
        return targetForeignKeyJavaType;
    }

    public String sqlJoinTableName(Dialect dialect) {
        requireOwning("sqlJoinTableName");
        String table = dialect.sqlName(joinTableIdentifier);
        if (joinTableSchemaIdentifier == null) {
            return table;
        }
        return dialect.sqlName(joinTableSchemaIdentifier) + "." + table;
    }

    /**
     * Owning-side metadata: this field, or the {@code mappedBy} field on the target entity.
     */
    public ManyToManyField owningSide(EntityModelRegistry registry) {
        if (owning()) {
            return this;
        }
        EntityModel targetModel = registry.get(targetEntityClass);
        ManyToManyField owning = targetModel.manyToManyByFieldName(mappedBy);
        if (!owning.owning()) {
            throw new MicroOrmException("mappedBy '" + mappedBy + "' on " + javaField.getDeclaringClass().getName()
                    + "." + javaField.getName() + " must refer to the owning @ManyToMany");
        }
        return owning;
    }

    @Override
    public boolean cascades(CascadeType type) {
        return Cascades.enabled(cascade, type);
    }

    private void requireOwning(String attribute) {
        if (!owning()) {
            throw new MicroOrmException("Inverse @ManyToMany has no " + attribute + " on "
                    + javaField.getDeclaringClass().getName() + "." + javaField.getName());
        }
    }
}
