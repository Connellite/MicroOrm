package io.github.connellite.microorm.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Fluent, named-parameter query builder for selecting mapped entities.
 * <p>
 * {@code EntityQuery} intentionally covers the lightweight ORM use case: one root entity,
 * composable {@code WHERE} predicates, {@code ORDER BY}, and optional {@code LIMIT}/{@code OFFSET}.
 * For joins, projections, and database-specific SQL, use {@link io.github.connellite.microorm.sql.Query}.
 *
 * <pre>{@code
 * EntityQuery<User> query = EntityQuery.of(User.class)
 *         .where(EntityQuery.field("name").like("Ada%"))
 *         .and(EntityQuery.field("enabled").eq(true))
 *         .orderBy(EntityQuery.field("name").asc())
 *         .limit(20);
 *
 * List<User> users = session.selectRows(query);
 * }</pre>
 *
 * @param <T> entity type selected by this query
 */
public final class EntityQuery<T> {

    private final Class<T> entityType;
    private Criterion criterion;
    private final List<Order> orders = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    private EntityQuery(Class<T> entityType) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    /**
     * Starts a query for the given entity type.
     *
     * @param entityType registered entity class
     * @param <T> entity type
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

    /** Entity class selected by this query. */
    public Class<T> entityType() {
        return entityType;
    }

    /** Optional root {@code WHERE} criterion. */
    public Criterion criterion() {
        return criterion;
    }

    /** Immutable sort order list. */
    public List<Order> orders() {
        return List.copyOf(orders);
    }

    /** Optional maximum number of rows to return. */
    public OptionalInt limit() {
        return limit == null ? OptionalInt.empty() : OptionalInt.of(limit);
    }

    /** Optional number of rows to skip. */
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
}
