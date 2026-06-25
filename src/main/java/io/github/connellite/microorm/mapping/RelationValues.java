package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.MicroOrmException;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.jdbc.EntityHydrator;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.type.JdbcValueMapper;

/**
 * Resolves {@link LazyRef} values to JDBC join-column parameters.
 */
public final class RelationValues {

    private RelationValues() {
    }

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
        LazyRef<?> ref = LazyRef.get(relation, entity);
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

    public static Object resolveRawForeignKey(LazyRef<?> ref, ManyToOneField relation, EntityModelRegistry registry) {
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

    public static boolean isNew(Object entity, EntityModel model) {
        return EntityHydrator.isUnsetPk(entity, model.primaryKey());
    }
}
