package io.github.connellite.microorm.sql;

import io.github.connellite.microorm.mapping.EntityModel;

/**
 * Runtime checks needed while rendering relation writes.
 */
@FunctionalInterface
public interface RelationWriteContext {

    /**
     * Returns whether the given attached entity already has a persistent row.
     */
    boolean existsByPrimaryKey(Object entity, EntityModel model);
}
