package io.github.connellite.microorm.repository;

import io.github.connellite.microorm.annotation.Param;
import io.github.connellite.microorm.annotation.Procedure;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.query.EntitySelect;
import io.github.connellite.microorm.session.Session;
import io.github.connellite.microorm.sql.Query;
import io.github.connellite.reflection.ReflectionUtil;
import lombok.experimental.UtilityClass;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.invoke.MethodHandles;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates dynamic proxies for {@link EntityRepository} interfaces.
 */
@UtilityClass
public final class RepositoryProxyFactory {

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
            var queryAnnotation = method.getAnnotation(io.github.connellite.microorm.annotation.Query.class);
            var procedureAnnotation = method.getAnnotation(Procedure.class);
            int nativeAnnotationCount = (queryAnnotation == null ? 0 : 1)
                    + (procedureAnnotation == null ? 0 : 1);
            if (nativeAnnotationCount > 1) {
                throw new MicroOrmException("Repository method cannot declare more than one native SQL annotation: "
                        + repositoryType.getName() + "." + method.getName());
            }
            if (queryAnnotation != null) {
                Object[] arguments = args == null ? new Object[0] : args;
                return executor.execute(session -> invokeNativeQuery(
                        session, method, buildNativeQuery(method, arguments, queryAnnotation.value(), "@Query"), "@Query"));
            }
            if (procedureAnnotation != null) {
                Object[] arguments = args == null ? new Object[0] : args;
                if (method.getReturnType() != void.class && method.getReturnType() != Void.class) {
                    return executor.execute(session -> invokeFunction(
                            session, method, buildFunctionQuery(method, arguments, procedureAnnotation)));
                }
                return executor.execute(session -> invokeNativeQuery(
                        session, method, buildProcedureQuery(method, arguments, procedureAnnotation), "@Procedure"));
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
                if ("selectRows".equals(name) && EntitySelect.class.isAssignableFrom(parameters[0])) {
                    return session.selectRows((EntitySelect<?>) arg);
                }
                if ("selectOne".equals(name) && EntitySelect.class.isAssignableFrom(parameters[0])) {
                    return session.selectOne((EntitySelect<?>) arg);
                }
                if ("findOne".equals(name) && EntitySelect.class.isAssignableFrom(parameters[0])) {
                    return session.findOne((EntitySelect<?>) arg);
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

        private Object invokeNativeQuery(
                Session session,
                Method method,
                Query query,
                String annotationName) {
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class) {
                session.execute(query);
                return null;
            }
            if (returnType == int.class || returnType == Integer.class) {
                return session.execute(query);
            }
            if (returnType == long.class || returnType == Long.class) {
                return (long) session.execute(query);
            }
            if (returnType == boolean.class || returnType == Boolean.class) {
                return session.execute(query) > 0;
            }
            if (List.class.isAssignableFrom(returnType)) {
                return session.selectRows(entityType, query);
            }
            if (Optional.class.isAssignableFrom(returnType)) {
                return session.findOne(entityType, query);
            }
            if (entityType.isAssignableFrom(returnType)) {
                return session.selectOne(entityType, query);
            }
            throw new MicroOrmException("Unsupported " + annotationName + " return type " + returnType.getName()
                    + " for " + repositoryType.getName() + "." + method.getName());
        }

        private Query buildProcedureQuery(
                Method method,
                Object[] args,
                Procedure annotation) {
            String procedure = annotation.value().isBlank() ? annotation.procedureName() : annotation.value();
            if (procedure.isBlank()) {
                procedure = method.getName();
            }
            return buildNativeQuery(method, args, procedureSql(method, args, procedure), "@Procedure");
        }

        private Query buildFunctionQuery(
                Method method,
                Object[] args,
                Procedure annotation) {
            String function = annotation.value().isBlank() ? annotation.procedureName() : annotation.value();
            if (function.isBlank()) {
                function = method.getName();
            }
            return buildNativeQuery(method, args, functionSql(method, args, function), "@Procedure");
        }

        private Object invokeFunction(Session session, Method method, Query query) {
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class) {
                throw new MicroOrmException("Unsupported stored function return type void for "
                        + repositoryType.getName() + "." + method.getName());
            }
            if (List.class.isAssignableFrom(returnType)
                    || Optional.class.isAssignableFrom(returnType)
                    || entityType.isAssignableFrom(returnType)) {
                return invokeNativeQuery(session, method, query, "@Procedure");
            }
            Class<?> scalarType = ReflectionUtil.primitiveToWrapper(returnType);
            return session.selectScalar(query, scalarType);
        }

        private String functionSql(Method method, Object[] args, String function) {
            if (looksLikeNativeSql(function)) {
                return function;
            }
            return "SELECT " + function + "(" + String.join(", ", parameterPlaceholders(method, args, "@Procedure")) + ")";
        }

        private String procedureSql(Method method, Object[] args, String procedure) {
            if (looksLikeNativeSql(procedure)) {
                return procedure;
            }
            return "CALL " + procedure + "(" + String.join(", ", parameterPlaceholders(method, args, "@Procedure")) + ")";
        }

        private List<String> parameterPlaceholders(Method method, Object[] args, String annotationName) {
            Parameter[] parameters = method.getParameters();
            List<String> placeholders = new ArrayList<>();
            for (int i = 0; i < parameters.length; i++) {
                Object value = args[i];
                Param param = parameters[i].getAnnotation(Param.class);
                if (param == null && value instanceof Map<?, ?> map) {
                    for (Object key : map.keySet()) {
                        if (!(key instanceof String name)) {
                            throw new MicroOrmException(annotationName + " Map parameter keys must be String for "
                                    + repositoryType.getName() + "." + method.getName());
                        }
                        placeholders.add(":" + name);
                    }
                    continue;
                }
                placeholders.add(":" + parameterName(method, parameters[i], param, annotationName));
            }
            return placeholders;
        }

        private boolean looksLikeNativeSql(String procedure) {
            String trimmed = procedure.trim();
            String lower = trimmed.toLowerCase();
            return trimmed.startsWith("{") || lower.startsWith("select ") || lower.startsWith("call ")
                    || lower.startsWith("exec ") || lower.startsWith("execute ");
        }

        private Query buildNativeQuery(Method method, Object[] args, String sql, String annotationName) {
            Query query = Query.of(sql);
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Object value = args[i];
                Param param = parameters[i].getAnnotation(Param.class);
                if (param == null && value instanceof Map<?, ?> map) {
                    bindMap(query, method, map, annotationName);
                    continue;
                }
                bindValue(query, parameterName(method, parameters[i], param, annotationName), value);
            }
            return query;
        }

        private void bindMap(Query query, Method method, Map<?, ?> values, String annotationName) {
            Map<String, Object> namedValues = new HashMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String name)) {
                    throw new MicroOrmException(annotationName + " Map parameter keys must be String for "
                            + repositoryType.getName() + "." + method.getName());
                }
                namedValues.put(name, entry.getValue());
            }
            query.setAll(namedValues);
        }

        private void bindValue(Query query, String name, Object value) {
            if (value instanceof Collection<?> collection) {
                query.setCollection(name, collection);
                return;
            }
            if (value != null && value.getClass().isArray()) {
                query.setCollection(name, arrayValues(value));
                return;
            }
            query.set(name, value);
        }

        private Collection<?> arrayValues(Object array) {
            int length = Array.getLength(array);
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(array, i));
            }
            return values;
        }

        private String parameterName(Method method, Parameter parameter, Param param, String annotationName) {
            if (param != null) {
                if (param.value().isBlank()) {
                    throw new MicroOrmException("@Param value cannot be blank for "
                            + repositoryType.getName() + "." + method.getName());
                }
                return param.value();
            }
            if (parameter.isNamePresent()) {
                return parameter.getName();
            }
            throw new MicroOrmException(annotationName + " parameter names are not available for "
                    + repositoryType.getName() + "." + method.getName() + "; add @Param to each argument");
        }

        private Object unsupported(Method method) {
            throw new MicroOrmException("Repository method is not backed by Session: " + repositoryType.getName() + "." + method.getName());
        }
    }
}
