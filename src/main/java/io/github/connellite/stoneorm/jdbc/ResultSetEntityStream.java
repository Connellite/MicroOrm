package io.github.connellite.stoneorm.jdbc;

import io.github.connellite.jdbc.NamedPreparedStatement;
import io.github.connellite.stoneorm.StoneOrmException;
import io.github.connellite.stoneorm.mapping.EntityModel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class ResultSetEntityStream {

    private ResultSetEntityStream() {
    }

    static <T> Stream<T> stream(
            NamedPreparedStatement statement,
            ResultSet resultSet,
            EntityModel model,
            Collection<String> availableColumns) {
        Iterator<T> iterator = new Iterator<>() {
            private boolean advanced;
            private boolean hasNext;

            @Override
            public boolean hasNext() {
                if (!advanced) {
                    try {
                        hasNext = resultSet.next();
                        advanced = true;
                    } catch (SQLException e) {
                        throw StoneOrmException.wrap(e);
                    }
                }
                return hasNext;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                advanced = false;
                try {
                    return EntityHydrator.mapRow(model, resultSet, availableColumns);
                } catch (SQLException e) {
                    throw StoneOrmException.wrap(e);
                }
            }
        };

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
                .onClose(() -> closeQuietly(resultSet, statement));
    }

    private static void closeQuietly(ResultSet resultSet, NamedPreparedStatement statement) {
        try (statement) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                throw StoneOrmException.wrap(e);
            }
        } catch (SQLException e) {
            throw StoneOrmException.wrap(e);
        }
    }
}
