package io.github.connellite.microorm.mapping;

import io.github.connellite.collections.ConcurrentReferenceHashMap;
import io.github.connellite.collections.MultiValueHashMap;
import io.github.connellite.microorm.exception.MicroOrmException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;

/**
 * Discovers, validates, and invokes entity lifecycle callback methods.
 */
public final class LifecycleCallbacks {

    private static final int CACHE_INITIAL_CAPACITY = 64;
    private static final ConcurrentReferenceHashMap<Class<?>, MultiValueHashMap<LifecycleEvent, Method>> CACHE =
            new ConcurrentReferenceHashMap<>(CACHE_INITIAL_CAPACITY, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private LifecycleCallbacks() {
    }

    /**
     * Validates lifecycle callback methods declared by the entity class.
     */
    public static void validate(Class<?> entityClass) {
        callbacks(entityClass);
    }

    /**
     * Invokes the callback for {@code event}, if the entity declares one.
     */
    public static void invoke(Object entity, LifecycleEvent event) {
        List<Method> methods = callbacks(entity.getClass()).get(event);
        if (methods == null) {
            return;
        }
        for (Method method : methods) {
            try {
                method.invoke(entity);
            } catch (IllegalAccessException e) {
                throw new MicroOrmException("Cannot invoke lifecycle callback " + callbackName(entity.getClass(), method, event), e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new MicroOrmException("Lifecycle callback failed " + callbackName(entity.getClass(), method, event), cause);
            }
        }
    }

    private static MultiValueHashMap<LifecycleEvent, Method> callbacks(Class<?> entityClass) {
        return CACHE.computeIfAbsent(entityClass, LifecycleCallbacks::scan);
    }

    private static MultiValueHashMap<LifecycleEvent, Method> scan(Class<?> entityClass) {
        MultiValueHashMap<LifecycleEvent, Method> callbacks = new MultiValueHashMap<>(LifecycleEvent.values().length);
        for (Method method : entityClass.getDeclaredMethods()) {
            for (LifecycleEvent event : LifecycleEvent.values()) {
                if (!method.isAnnotationPresent(event.annotationType())) {
                    continue;
                }
                validateMethod(entityClass, method, event);
                callbacks.add(event, method);
            }
        }
        callbacks.replaceAll((event, methods) -> methods.stream()
                .sorted(Comparator.comparing(Method::getName))
                .toList());
        return callbacks;
    }

    private static void validateMethod(Class<?> entityClass, Method method, LifecycleEvent event) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new MicroOrmException("Lifecycle callback cannot be static: " + callbackName(entityClass, method, event));
        }
        if (method.getParameterCount() != 0) {
            throw new MicroOrmException("Lifecycle callback must not declare parameters: " + callbackName(entityClass, method, event));
        }
        if (method.getReturnType() != void.class) {
            throw new MicroOrmException("Lifecycle callback must return void: " + callbackName(entityClass, method, event));
        }
        method.trySetAccessible();
    }

    private static String callbackName(Class<?> entityClass, Method method, LifecycleEvent event) {
        return "@" + event.annotationType().getSimpleName()
                + " " + entityClass.getName() + "." + method.getName() + "()";
    }
}
