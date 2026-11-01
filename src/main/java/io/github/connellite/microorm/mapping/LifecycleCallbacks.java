package io.github.connellite.microorm.mapping;

import io.github.connellite.collections.ConcurrentReferenceHashMap;
import io.github.connellite.collections.MultiValueHashMap;
import io.github.connellite.microorm.annotation.EntityListeners;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.reflection.ReflectionUtil;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers, validates, and invokes entity lifecycle callbacks and {@link EntityListeners}.
 * <p>
 * Entity methods keep their existing name order. Listeners are collected from the farthest superclass
 * to the entity class, in {@link EntityListeners#value()} order. Pre-events run listeners first;
 * post-events run entity methods first and then listeners in reverse.
 */
@UtilityClass
public final class LifecycleCallbacks {

    private static final int CACHE_INITIAL_CAPACITY = 64;
    private static final Map<Class<?>, MultiValueHashMap<LifecycleEvent, Callback>> CACHE =
            new ConcurrentReferenceHashMap<>(CACHE_INITIAL_CAPACITY, ConcurrentReferenceHashMap.ReferenceType.WEAK);
    private static final Map<Class<?>, Object> LISTENERS =
            new ConcurrentReferenceHashMap<>(CACHE_INITIAL_CAPACITY, ConcurrentReferenceHashMap.ReferenceType.WEAK);;


    /**
     * Validates lifecycle callback methods declared by the entity class and its listeners.
     */
    public static void validate(Class<?> entityClass) {
        callbacks(entityClass);
    }

    /**
     * Invokes callbacks for {@code event}, including entity listeners.
     */
    public static void invoke(Object entity, LifecycleEvent event) {
        List<Callback> callbacks = callbacks(entity.getClass()).get(event);
        if (callbacks == null) {
            return;
        }
        for (Callback callback : callbacks) {
            try {
                if (callback.listener == null) {
                    ReflectionUtil.invoke(callback.method, entity);
                } else {
                    ReflectionUtil.invoke(callback.method, callback.listener, entity);
                }
            } catch (IllegalAccessException e) {
                throw new MicroOrmException("Cannot invoke lifecycle callback " + callbackName(entity, callback, event), e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new MicroOrmException("Lifecycle callback failed " + callbackName(entity, callback, event), cause);
            }
        }
    }

    private static MultiValueHashMap<LifecycleEvent, Callback> callbacks(Class<?> entityClass) {
        return CACHE.computeIfAbsent(entityClass, LifecycleCallbacks::scan);
    }

    private static MultiValueHashMap<LifecycleEvent, Callback> scan(Class<?> entityClass) {
        Map<LifecycleEvent, List<Callback>> entityMethods = entityMethods(entityClass);
        Map<LifecycleEvent, List<List<Callback>>> listenerGroups = listenerGroups(entityClass);
        MultiValueHashMap<LifecycleEvent, Callback> callbacks = new MultiValueHashMap<>(LifecycleEvent.values().length);
        for (LifecycleEvent event : LifecycleEvent.values()) {
            List<Callback> entity = entityMethods.getOrDefault(event, List.of());
            List<List<Callback>> groups = listenerGroups.getOrDefault(event, List.of());
            List<Callback> ordered = new ArrayList<>(entity.size() + groups.size());
            if (event.post()) {
                ordered.addAll(entity);
                for (int i = groups.size() - 1; i >= 0; i--) {
                    ordered.addAll(groups.get(i));
                }
            } else {
                for (List<Callback> group : groups) {
                    ordered.addAll(group);
                }
                ordered.addAll(entity);
            }
            for (Callback callback : ordered) {
                callbacks.add(event, callback);
            }
        }
        return callbacks;
    }

    private static Map<LifecycleEvent, List<Callback>> entityMethods(Class<?> entityClass) {
        Map<LifecycleEvent, List<Callback>> methods = new EnumMap<>(LifecycleEvent.class);
        for (LifecycleEvent event : LifecycleEvent.values()) {
            List<Callback> callbacks = new ArrayList<>();
            for (Method method : ReflectionUtil.getAllMethodsByAnnotation(entityClass, event.annotationType())) {
                validateEntityMethod(entityClass, method, event);
                callbacks.add(new Callback(method, null));
            }
            if (!callbacks.isEmpty()) {
                callbacks.sort(Comparator.comparing(callback -> callback.method.getName()));
                methods.put(event, List.copyOf(callbacks));
            }
        }
        return methods;
    }

    private static Map<LifecycleEvent, List<List<Callback>>> listenerGroups(Class<?> entityClass) {
        Map<LifecycleEvent, List<List<Callback>>> groups = new EnumMap<>(LifecycleEvent.class);
        for (Class<?> type : hierarchy(entityClass)) {
            EntityListeners listeners = type.getAnnotation(EntityListeners.class);
            if (listeners == null) {
                continue;
            }
            for (Class<?> listenerClass : listeners.value()) {
                Object listener = LISTENERS.computeIfAbsent(listenerClass, LifecycleCallbacks::newListener);;
                for (LifecycleEvent event : LifecycleEvent.values()) {
                    List<Callback> group = listenerCallbacks(entityClass, listenerClass, listener, event);
                    if (!group.isEmpty()) {
                        groups.computeIfAbsent(event, ignored -> new ArrayList<>()).add(group);
                    }
                }
            }
        }
        return groups;
    }

    private static List<Callback> listenerCallbacks(Class<?> entityClass, Class<?> listenerClass, Object listener,
                                                    LifecycleEvent event) {
        List<Callback> callbacks = new ArrayList<>();
        for (Class<?> type = listenerClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : ReflectionUtil.getAllMethodsByAnnotation(type, event.annotationType())) {
                validateListenerMethod(entityClass, method, event);
                callbacks.add(new Callback(method, listener));
            }
        }
        callbacks.sort(Comparator.comparing(callback -> callback.method.getName()));
        return List.copyOf(callbacks);
    }

    /** Farthest superclass first, then the entity class. */
    private static List<Class<?>> hierarchy(Class<?> entityClass) {
        ArrayDeque<Class<?>> types = new ArrayDeque<>();
        for (Class<?> type = entityClass; type != null && type != Object.class; type = type.getSuperclass()) {
            types.addFirst(type);
        }
        return List.copyOf(types);
    }

    private static Object newListener(Class<?> listenerClass) {
        if (listenerClass.isInterface() || Modifier.isAbstract(listenerClass.getModifiers())) {
            throw new MicroOrmException("Entity listener must be a concrete class with a public no-arg constructor: " + listenerClass.getName());
        }
        try {
            Constructor<?> constructor = ReflectionUtil.getConstructor(listenerClass);
            if (!Modifier.isPublic(constructor.getModifiers())) {
                throw new MicroOrmException("Entity listener requires a public no-arg constructor: " + listenerClass.getName());
            }
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new MicroOrmException("Entity listener requires a public no-arg constructor: " + listenerClass.getName(), e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            throw new MicroOrmException("Cannot create entity listener " + listenerClass.getName(), cause);
        }
    }

    private static void validateEntityMethod(Class<?> entityClass, Method method, LifecycleEvent event) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new MicroOrmException("Lifecycle callback cannot be static: " + callbackName(entityClass, method, event));
        }
        if (method.getParameterCount() != 0) {
            throw new MicroOrmException("Lifecycle callback must not declare parameters: " + callbackName(entityClass, method, event));
        }
        if (method.getReturnType() != void.class) {
            throw new MicroOrmException("Lifecycle callback must return void: " + callbackName(entityClass, method, event));
        }
        ReflectionUtil.open(method);
    }

    private static void validateListenerMethod(Class<?> entityClass, Method method, LifecycleEvent event) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new MicroOrmException("Lifecycle callback cannot be static: " + callbackName(method.getDeclaringClass(), method, event));
        }
        if (method.getReturnType() != void.class) {
            throw new MicroOrmException("Lifecycle callback must return void: " + callbackName(method.getDeclaringClass(), method, event));
        }
        if (method.getParameterCount() != 1) {
            throw new MicroOrmException(
                    "Lifecycle callback must declare exactly one parameter: " + callbackName(method.getDeclaringClass(), method, event));
        }
        Class<?> parameterType = method.getParameterTypes()[0];
        if (!parameterType.isAssignableFrom(entityClass)) {
            throw new MicroOrmException("Lifecycle callback parameter must accept " + entityClass.getName()
                    + ": " + callbackName(method.getDeclaringClass(), method, event));
        }
        ReflectionUtil.open(method);
    }

    private static String callbackName(Object entity, Callback callback, LifecycleEvent event) {
        Class<?> owner = callback.listener == null ? entity.getClass() : callback.listener.getClass();
        return callbackName(owner, callback.method, event);
    }

    private static String callbackName(Class<?> owner, Method method, LifecycleEvent event) {
        return "@" + event.annotationType().getSimpleName()  + " " + owner.getName() + "." + method.getName() + "()";
    }

    private record Callback(Method method, Object listener) {
    }
}
