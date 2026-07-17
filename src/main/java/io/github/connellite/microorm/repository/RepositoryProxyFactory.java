package io.github.connellite.microorm.repository;

import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.query.EntityQuery;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.reflection.ReflectionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.invoke.MethodHandles;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Creates dynamic proxies for {@link EntityRepository} interfaces.
 */
public final class RepositoryProxyFactory {

    private RepositoryProxyFactory() {
    }

    @FunctionalInterface
    public interface RepositoryOperation<T> {
        T apply(Session session) throws SQLException;
    }

    @FunctionalInterface
    public interface SessionExecutor {
        Object execute(RepositoryOperation<?> operation) throws SQLException;
    }

    public static <R extends EntityRepository<?, ?>> R create(Class<R> repositoryType, SessionExecutor executor) {
        Objects.requireNonNull(repositoryType, "repositoryType");
        Objects.requireNonNull(executor, "executor");
        if (!repositoryType.isInterface()) {
            throw new MicroOrmException("Repository type must be an interface: " + repositoryType.getName());
        }
        Class<?> entityType = resolveEntityType(repositoryType);
        InvocationHandler handler = new RepositoryInvocationHandler(repositoryType, entityType, executor);
        Object proxy = Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                handler);
        return repositoryType.cast(proxy);
    }

    private static Class<?> resolveEntityType(Class<?> repositoryType) {
        Type entityType = resolveEntityType(repositoryType, Map.of());
        if (entityType instanceof Class<?> entityClass) {
            return entityClass;
        }
        throw new MicroOrmException("Cannot resolve entity type for repository " + repositoryType.getName() + "; declare it as EntityRepository<Entity, Id>");
    }

    private static Type resolveEntityType(Type type, Map<TypeVariable<?>, Type> variables) {
        if (type instanceof Class<?> clazz) {
            for (Type genericInterface : clazz.getGenericInterfaces()) {
                Type resolved = resolveEntityType(genericInterface, variables);
                if (resolved != null) {
                    return resolved;
                }
            }
            return null;
        }
        if (!(type instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        Class<?> rawType = (Class<?>) parameterizedType.getRawType();
        Map<TypeVariable<?>, Type> nextVariables = new HashMap<>(variables);
        TypeVariable<?>[] parameters = rawType.getTypeParameters();
        Type[] arguments = parameterizedType.getActualTypeArguments();
        for (int i = 0; i < parameters.length; i++) {
            nextVariables.put(parameters[i], resolveType(arguments[i], variables));
        }
        if (rawType == EntityRepository.class) {
            Type entityArgument = resolveType(arguments[0], variables);
            if (entityArgument instanceof Class<?>) {
                return entityArgument;
            }
            List<Class<?>> classes = ReflectionUtil.getAllGenericParameterClasses(entityArgument);
            return classes.isEmpty() ? entityArgument : classes.get(0);
        }
        return resolveEntityType(rawType, nextVariables);
    }

    private static Type resolveType(Type type, Map<TypeVariable<?>, Type> variables) {
        // intermediate interface variables, e.g. Repo -> BaseRepo<User, Long> -> EntityRepository<T, ID>.
        while (type instanceof TypeVariable<?> variable && variables.containsKey(variable)) {
            type = variables.get(variable);
        }
        return type;
    }

    private record RepositoryInvocationHandler(
            Class<?> repositoryType,
            Class<?> entityType,
            SessionExecutor executor) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            if (method.isDefault()) {
                return invokeDefaultMethod(proxy, method, args);
            }
            try {
                return executor.execute(session -> invokeRepositoryMethod(session, method, args == null ? new Object[0] : args));
            } catch (SQLException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new MicroOrmException("Cannot invoke repository method " + repositoryType.getName() + "." + method.getName(), e);
            }
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> repositoryType.getName() + " repository for " + entityType.getName();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new MicroOrmException("Unsupported Object method: " + method.getName());
            };
        }

        private Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
            return MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup())
                    .unreflectSpecial(method, method.getDeclaringClass())
                    .bindTo(proxy)
                    .invokeWithArguments(args == null ? new Object[0] : args);
        }

        @SuppressWarnings("unchecked")
        private Object invokeRepositoryMethod(Session session, Method method, Object[] args) throws SQLException {
            String name = method.getName();
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 0) {
                return switch (name) {
                    case "createEntity" -> {
                        session.createEntity(entityType);
                        yield null;
                    }
                    case "syncEntity" -> {
                        session.syncEntity(entityType);
                        yield null;
                    }
                    case "updateEntity" -> {
                        session.updateEntity(entityType);
                        yield null;
                    }
                    case "dropEntity" -> {
                        session.dropEntity(entityType);
                        yield null;
                    }
                    case "deleteAllRows" -> session.deleteAllRows(entityType);
                    case "selectRows" -> session.selectRows(entityType);
                    default -> unsupported(method);
                };
            }
            if (parameters.length == 1) {
                Object arg = args[0];
                switch (name) {
                    case "insertRow" -> {
                        return session.insertRow(arg);
                    }
                    case "updateRow" -> {
                        return session.updateRow(arg);
                    }
                    case "deleteRow" -> {
                        return session.deleteRow(arg);
                    }
                    case "deleteById" -> {
                        return session.deleteById(entityType, arg);
                    }
                    case "existsById" -> {
                        return session.existsById(entityType, arg);
                    }
                    case "selectRow" -> {
                        return session.selectRow(entityType, arg);
                    }
                    case "findById" -> {
                        return session.findById(entityType, arg);
                    }
                }
                if ("insertRows".equals(name) && List.class.isAssignableFrom(parameters[0])) {
                    return session.insertRows((List<?>) arg);
                }
                if ("selectRows".equals(name) && Map.class.isAssignableFrom(parameters[0])) {
                    return session.selectRows(entityType, (Map<String, ?>) arg);
                }
                if ("selectRows".equals(name) && EntityQuery.class.isAssignableFrom(parameters[0])) {
                    return session.selectRows((EntityQuery<?>) arg);
                }
                if ("selectOne".equals(name) && EntityQuery.class.isAssignableFrom(parameters[0])) {
                    return session.selectOne((EntityQuery<?>) arg);
                }
                if ("findOne".equals(name) && EntityQuery.class.isAssignableFrom(parameters[0])) {
                    return session.findOne((EntityQuery<?>) arg);
                }
                if ("selectRows".equals(name) && Query.class.isAssignableFrom(parameters[0])) {
                    return session.selectRows(entityType, (Query) arg);
                }
                if ("selectOne".equals(name) && Query.class.isAssignableFrom(parameters[0])) {
                    return session.selectOne(entityType, (Query) arg);
                }
                if ("findOne".equals(name) && Query.class.isAssignableFrom(parameters[0])) {
                    return session.findOne(entityType, (Query) arg);
                }
            }
            if (parameters.length == 2
                    && "insertRows".equals(name)
                    && List.class.isAssignableFrom(parameters[0])
                    && parameters[1] == int.class) {
                return session.insertRows((List<?>) args[0], (Integer) args[1]);
            }
            return unsupported(method);
        }

        private Object unsupported(Method method) {
            throw new MicroOrmException("Repository method is not backed by Session: " + repositoryType.getName() + "." + method.getName());
        }
    }
}
