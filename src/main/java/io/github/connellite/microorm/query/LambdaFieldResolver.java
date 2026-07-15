package io.github.connellite.microorm.query;

import io.github.connellite.collections.ConcurrentReferenceHashMap;
import io.github.connellite.reflection.ReflectionUtil;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves serializable getter method references to mapped Java property names.
 */
final class LambdaFieldResolver {

    private static final ConcurrentMap<Class<?>, SerializedLambda> LAMBDA_CACHE =
            new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private LambdaFieldResolver() {
    }

    static String fieldName(Serializable getter) {
        return PropertyNamer.methodToProperty(extractLambda(getter).getImplMethodName());
    }

    // Based on MyBatis-Plus LambdaUtils.extract:
    // https://github.com/baomidou/mybatis-plus/blob/3.0/mybatis-plus-core/src/main/java/com/baomidou/mybatisplus/core/toolkit/LambdaUtils.java
    private static SerializedLambda extractLambda(Serializable getter) {
        return LAMBDA_CACHE.computeIfAbsent(getter.getClass(), ignored -> serializedLambda(getter));
    }

    private static SerializedLambda serializedLambda(Serializable getter) {
        try {
            return (SerializedLambda) ReflectionUtil.invoke(getter, "writeReplace");
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalArgumentException("Cannot inspect field method reference", e);
        }
    }
}
