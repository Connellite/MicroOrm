package io.github.connellite.microorm.jdbc;

import io.github.connellite.jdbc.NamedPreparedStatement;
import io.github.connellite.microorm.dialect.Dialect;
import io.github.connellite.microorm.dynamic.DynamicTable;
import io.github.connellite.microorm.dynamic.DynamicValueBinder;
import io.github.connellite.microorm.dynamic.MapRowMapper;
import io.github.connellite.microorm.exception.MicroOrmException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class ResultSetDynamicMapStream {

    private ResultSetDynamicMapStream() {
    }

    static Stream<Map<String, Object>> stream(
            NamedPreparedStatement statement,
            ResultSet resultSet,
            DynamicTable table,
            Dialect dialect,
            DynamicValueBinder binder,
            Collection<String> availableColumns) {
        Iterator<Map<String, Object>> iterator = new Iterator<>() {
            private boolean advanced;
            private boolean hasNext;

            @Override
            public boolean hasNext() {
                if (!advanced) {
                    try {
                        hasNext = resultSet.next();
                        advanced = true;
                    } catch (SQLException e) {
                        throw MicroOrmException.wrap(e);
                    }
                }
                return hasNext;
            }

            @Override
            public Map<String, Object> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                advanced = false;
                try {
                    return MapRowMapper.mapRow(resultSet, table, dialect, binder, availableColumns);
                } catch (SQLException e) {
                    throw MicroOrmException.wrap(e);
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
                throw MicroOrmException.wrap(e);
            }
        } catch (SQLException e) {
            throw MicroOrmException.wrap(e);
        }
    }
}
