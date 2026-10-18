package io.github.connellite.microorm.type;

import io.github.connellite.collections.ConcurrentReferenceHashMap;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.reflection.ReflectionUtil;
import lombok.experimental.UtilityClass;

/** One instance per {@link JdbcType} class. */
@UtilityClass
public final class JdbcTypes {

    private static final int CACHE_INITIAL_CAPACITY = 16;

    private static final ConcurrentReferenceHashMap<Class<? extends JdbcType>, JdbcType> INSTANCES =
            new ConcurrentReferenceHashMap<>(CACHE_INITIAL_CAPACITY, ConcurrentReferenceHashMap.ReferenceType.WEAK);


    public static JdbcType get(Class<? extends JdbcType> type) {
        return INSTANCES.computeIfAbsent(type, JdbcTypes::create);
    }

    private static JdbcType create(Class<? extends JdbcType> type) {
        try {
            return ReflectionUtil.getInstance(type);
        } catch (ReflectiveOperationException e) {
            throw new MicroOrmException("Cannot instantiate JdbcType " + type.getName(), e);
        }
    }
}
