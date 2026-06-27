package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.jdbc.EntityHydrator;
import io.github.connellite.microorm.relation.EntityRef;
import io.github.connellite.microorm.type.JdbcValueMapper;

/**
 * Resolves relation reference values to JDBC join-column parameters
 * during insert, update, and relation graph persistence.
 */
public final class RelationValues {

    private RelationValues() {
    }

    /** Join-column JDBC value for a {@link ManyToOneField} on {@code entity} (no cyclic deferral). */
    public static Object joinColumnValue(
            Object entity,
            EntityModel model,
            ManyToOneField relation,
            EntityModelRegistry registry,
            Dialect dialect) {
        return joinColumnValue(entity, model, relation, registry, dialect, null);
    }

    /**
     * @param deferIfUnresolved when true and the referenced entity has no primary key yet, returns {@code null}
     *                          (for cyclic graphs — second pass applies the FK update).
     */
    public static Object joinColumnValue(
            Object entity,
            EntityModel model,
            ManyToOneField relation,
            EntityModelRegistry registry,
            Dialect dialect,
            Boolean deferIfUnresolved) {
        EntityRef<?> ref = EntityRef.get(relation, entity);
        if (ref == null) {
            if (!relation.nullable()) {
                throw new MicroOrmException("Required @ManyToOne '" + relation.javaField().getName()
                        + "' is null on " + model.entityClass().getName());
            }
            return null;
        }
        Object rawKey = resolveRawForeignKey(ref, relation, registry);
        if (rawKey == null) {
            if (!relation.nullable()) {
                if (Boolean.TRUE.equals(deferIfUnresolved)) {
                    return null;
                }
                throw new MicroOrmException("Required @ManyToOne '" + relation.javaField().getName()
                        + "' has no resolvable primary key on " + model.entityClass().getName());
            }
            return null;
        }
        EntityModel targetModel = registry.get(relation.targetEntityClass());
        JdbcValueMapper mapper = dialect.valueMapper();
        return mapper.toJdbcValue(targetModel.primaryKey(), rawKey);
    }

    /**
     * Primary key or explicit foreign key held by a relation reference wrapper,
     * without loading the target row from the database.
     */
    public static Object resolveRawForeignKey(EntityRef<?> ref, ManyToOneField relation, EntityModelRegistry registry) {
        if (ref.foreignKey() != null) {
            return ref.foreignKey();
        }
        Object attached = ref.attachedEntity();
        if (attached == null) {
            return null;
        }
        EntityModel targetModel = registry.get(relation.targetEntityClass());
        return EntityHydrator.getFieldValue(attached, targetModel.primaryKey());
    }

    /** {@code true} when the entity primary key is unset (new row for insert). */
    public static boolean isNew(Object entity, EntityModel model) {
        return EntityHydrator.isUnsetPk(entity, model.primaryKey());
    }
}
