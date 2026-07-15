package io.github.connellite.microorm.query;

import io.github.connellite.collections.ConcurrentReferenceHashMap;
import io.github.connellite.reflection.ReflectionUtil;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Fluent, named-parameter query builder for selecting mapped entities.
 * <p>
 * {@code EntityQuery} intentionally covers the lightweight ORM use case: one root entity,
 * relation joins for filtering/sorting, composable {@code WHERE} predicates, {@code ORDER BY},
 * and optional {@code LIMIT}/{@code OFFSET}. For projections and database-specific SQL, use
 * {@link io.github.connellite.microorm.sql.Query}.
 *
 * <pre>{@code
 * EntityQuery<User> query = EntityQuery.of(User.class)
 *         .where(EntityQuery.field(User::getName).like("Ada%"))
 *         .and(EntityQuery.field("enabled").eq(true))
 *         .orderBy(EntityQuery.field(User_.NAME).asc())
 *         .limit(20);
 *
 * EntityQuery<Order> joined = EntityQuery.of(Order.class)
 *         .leftJoin("customer")
 *         .where(EntityQuery.field("customer.name").eq("Acme"));
 *
 * List<User> users = session.selectRows(query);
 * }</pre>
 *
 * @param <T> entity type selected by this query
 */
public final class EntityQuery<T> {

    private final Class<T> entityType;
    private Criterion criterion;
    private final List<Join> joins = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private Integer limit;
    private Integer offset;
    private static final ConcurrentMap<Class<?>, SerializedLambda> LAMBDA_CACHE = new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private EntityQuery(Class<T> entityType) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    /**
     * Starts a query for the given entity type.
     *
     * @param entityType registered entity class
     * @param <T>        entity type
     * @return mutable query builder
     */
    public static <T> EntityQuery<T> of(Class<T> entityType) {
        return new EntityQuery<>(entityType);
    }

    /**
     * Creates a field reference for criteria and sort orders.
     * <p>
     * The field is resolved when the query is rendered, so unknown names fail at execution time
     * with the same mapping validation as built-in filtered selects.
     *
     * @param name Java field name or mapped column name
     * @return field path
     */
    public static FieldPath field(String name) {
        return new FieldPath(name);
    }

    /**
     * Creates a field reference from a JavaBean getter method reference, for example
     * {@code EntityQuery.field(User::getName)} or {@code EntityQuery.field(User::isEnabled)}.
     */
    public static <T, R> FieldPath field(Getter<T, R> getter) {
        Objects.requireNonNull(getter, "getter");
        return field(fieldNameFromGetter(getter));
    }

    /**
     * Creates a field reference from a lightweight static metamodel attribute, for example
     * {@code EntityQuery.field(User_.NAME)}.
     */
    public static FieldPath field(Attribute<?, ?> attribute) {
        Objects.requireNonNull(attribute, "attribute");
        return field(attribute.name());
    }

    /**
     * Creates a lightweight static metamodel attribute.
     *
     * <pre>{@code
     * final class User_ {
     *     static final EntityQuery.Attribute<User, String> NAME = EntityQuery.attribute("name");
     * }
     * }</pre>
     */
    public static <T, R> Attribute<T, R> attribute(String name) {
        return new Attribute<>(name);
    }

    /**
     * Serializable getter reference used by {@link #field(Getter)}.
     */
    @FunctionalInterface
    public interface Getter<T, R> extends Function<T, R>, Serializable {
    }

    /**
     * Lightweight static metamodel attribute used by {@link #field(Attribute)}.
     */
    public record Attribute<T, R>(String name) {
        public Attribute {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("attribute name cannot be blank");
            }
        }
    }

    /**
     * Entity class selected by this query.
     */
    public Class<T> entityType() {
        return entityType;
    }

    /**
     * Optional root {@code WHERE} criterion.
     */
    public Criterion criterion() {
        return criterion;
    }

    /**
     * Immutable join declaration list.
     */
    public List<Join> joins() {
        return List.copyOf(joins);
    }

    /**
     * Immutable sort order list.
     */
    public List<Order> orders() {
        return List.copyOf(orders);
    }

    /**
     * Optional maximum number of rows to return.
     */
    public OptionalInt limit() {
        return limit == null ? OptionalInt.empty() : OptionalInt.of(limit);
    }

    /**
     * Optional number of rows to skip.
     */
    public OptionalInt offset() {
        return offset == null ? OptionalInt.empty() : OptionalInt.of(offset);
    }

    /**
     * Replaces the current {@code WHERE} criterion.
     *
     * @param criterion expression to apply
     * @return this query for chaining
     */
    public EntityQuery<T> where(Criterion criterion) {
        this.criterion = Objects.requireNonNull(criterion, "criterion");
        return this;
    }

    /**
     * Adds an {@code AND} expression to the current {@code WHERE} clause.
     *
     * @param criterion expression to combine
     * @return this query for chaining
     */
    public EntityQuery<T> and(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.and(criterion);
        return this;
    }

    /**
     * Adds an {@code OR} expression to the current {@code WHERE} clause.
     *
     * @param criterion expression to combine
     * @return this query for chaining
     */
    public EntityQuery<T> or(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.or(criterion);
        return this;
    }

    /**
     * Adds a SQL {@code INNER JOIN} for a root relation field.
     *
     * @param relationName Java field name of a {@code @ManyToOne} or {@code @OneToMany} relation
     * @return this query for chaining
     */
    public EntityQuery<T> join(String relationName) {
        return join(relationName, JoinType.INNER);
    }

    /**
     * Adds a SQL {@code LEFT JOIN} for a root relation field.
     *
     * @param relationName Java field name of a {@code @ManyToOne} or {@code @OneToMany} relation
     * @return this query for chaining
     */
    public EntityQuery<T> leftJoin(String relationName) {
        return join(relationName, JoinType.LEFT);
    }

    /**
     * Adds a SQL {@code RIGHT JOIN} for a root relation field.
     *
     * @param relationName Java field name of a {@code @ManyToOne} or {@code @OneToMany} relation
     * @return this query for chaining
     */
    public EntityQuery<T> rightJoin(String relationName) {
        return join(relationName, JoinType.RIGHT);
    }

    /**
     * Adds a SQL {@code FULL JOIN} for a root relation field.
     *
     * @param relationName Java field name of a {@code @ManyToOne} or {@code @OneToMany} relation
     * @return this query for chaining
     */
    public EntityQuery<T> fullJoin(String relationName) {
        return join(relationName, JoinType.FULL);
    }

    /**
     * Adds a SQL {@code CROSS JOIN} for a root relation field. Cross joins intentionally omit
     * the relation {@code ON} predicate and should be used sparingly.
     *
     * @param relationName Java field name of a {@code @ManyToOne} or {@code @OneToMany} relation
     * @return this query for chaining
     */
    public EntityQuery<T> crossJoin(String relationName) {
        return join(relationName, JoinType.CROSS);
    }

    /**
     * Adds a join for a root relation field.
     *
     * @param relationName Java field name of a {@code @ManyToOne} or {@code @OneToMany} relation
     * @param type         SQL join type
     * @return this query for chaining
     */
    public EntityQuery<T> join(String relationName, JoinType type) {
        joins.add(new Join(relationName, type));
        return this;
    }

    /**
     * Appends sort orders to this query.
     *
     * @param orders one or more order items
     * @return this query for chaining
     */
    public EntityQuery<T> orderBy(Order... orders) {
        Objects.requireNonNull(orders, "orders");
        Arrays.stream(orders).map(order -> Objects.requireNonNull(order, "order")).forEach(this.orders::add);
        return this;
    }

    /**
     * Sets the maximum row count.
     *
     * @param limit positive row count
     * @return this query for chaining
     */
    public EntityQuery<T> limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
        return this;
    }

    /**
     * Sets the number of rows to skip before returning results.
     *
     * @param offset non-negative row count
     * @return this query for chaining
     */
    public EntityQuery<T> offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        this.offset = offset;
        return this;
    }

    private static String fieldNameFromGetter(Serializable getter) {
        return methodToProperty(extractLambda(getter).getImplMethodName());
    }

    //MyBatis-Plus LambdaUtils.extract:
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

    // MyBatis PropertyNamer.methodToProperty:
    // https://mybatis.org/mybatis-3/xref/org/apache/ibatis/reflection/property/PropertyNamer.html
    private static String methodToProperty(String name) {
        if (name.startsWith("is")) {
            name = name.substring(2);
        } else if (name.startsWith("get") || name.startsWith("set")) {
            name = name.substring(3);
        } else {
            throw new IllegalArgumentException(
                    "Error parsing property name '" + name + "'. Didn't start with 'is', 'get' or 'set'.");
        }

        if (name.length() == 1 || name.length() > 1 && !Character.isUpperCase(name.charAt(1))) {
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }
        return name;
    }
}
