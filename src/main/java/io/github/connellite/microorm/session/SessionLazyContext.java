package io.github.connellite.microorm.session;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.jdbc.SqlExecutor;
import io.github.connellite.microorm.mapping.EntityModel;
import io.github.connellite.microorm.mapping.EntityModelRegistry;
import io.github.connellite.microorm.mapping.ManyToOneField;
import io.github.connellite.microorm.mapping.OneToManyField;
import io.github.connellite.microorm.relation.LazyLoadContext;
import io.github.connellite.microorm.sql.SqlGenerator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

final class SessionLazyContext implements LazyLoadContext {

    private final Session session;
    private final Connection connection;
    private final EntityModelRegistry registry;
    private final SqlGenerator sql;
    private final Dialect dialect;
    private boolean closed;

    SessionLazyContext(Session session, Connection connection, EntityModelRegistry registry, Dialect dialect) {
        this.session = session;
        this.connection = connection;
        this.registry = registry;
        this.dialect = dialect;
        this.sql = dialect.sqlGenerator();
    }

    void markClosed() {
        closed = true;
    }

    @Override
    public void ensureOpen() {
        if (closed) {
            throw new MicroOrmException("Session is closed");
        }
        try {
            if (connection.isClosed()) {
                throw new MicroOrmException("Session is closed");
            }
        } catch (SQLException e) {
            throw MicroOrmException.wrap(e);
        }
    }

    @Override
    public <T> T loadById(Class<T> type, Object id) {
        ensureOpen();
        return session.selectRow(type, id, this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> loadCollection(OneToManyField relation, Object ownerId) {
        ensureOpen();
        EntityModel childModel = registry.get(relation.targetEntityClass());
        ManyToOneField inverse = childModel.manyToOneByFieldName(relation.mappedBy());
        EntityModel ownerModel = registry.get(inverse.targetEntityClass());
        Object jdbcValue = dialect.valueMapper().toJdbcValue(ownerModel.primaryKey(), ownerId);
        try (var rows = SqlExecutor.queryEntitiesStream(
                connection,
                sql.selectByJoinColumn(childModel, inverse.joinColumn(), jdbcValue),
                childModel,
                dialect,
                dialect.valueMapper(),
                this,
                registry)) {
            return (List<T>) rows.toList();
        }
    }
}
