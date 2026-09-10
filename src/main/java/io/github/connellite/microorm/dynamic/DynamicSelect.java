package io.github.connellite.microorm.dynamic;

import io.github.connellite.microorm.query.Criterion;
import io.github.connellite.microorm.query.EntitySelect;
import io.github.connellite.microorm.query.FieldPath;
import io.github.connellite.microorm.query.Order;
import io.github.connellite.microorm.sql.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Fluent SQL-oriented SELECT builder for one registered dynamic table. */
public final class DynamicSelect {

    private final String tableName;
    private final List<String> selectedColumns = new ArrayList<>();
    private final List<String> groupColumns = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private Criterion criterion;
    private Criterion havingCriterion;
    private Integer limit;
    private Integer offset;
    private boolean distinct;

    private DynamicSelect(String tableName) {
        this.tableName = requireName(tableName, "tableName");
    }

    /** Starts a SELECT for the registered dynamic table name. */
    public static DynamicSelect from(String tableName) {
        return new DynamicSelect(tableName);
    }

    /** Creates a column reference for criteria and sort orders. */
    public static FieldPath field(String columnName) {
        return new FieldPath(columnName);
    }

    /** Creates an SQL {@code EXISTS (...)} criterion from a named-parameter subquery. */
    public static Criterion exists(Query query) {
        return EntitySelect.exists(query);
    }

    /** Creates an SQL {@code NOT EXISTS (...)} criterion from a named-parameter subquery. */
    public static Criterion notExists(Query query) {
        return EntitySelect.notExists(query);
    }

    public String tableName() {
        return tableName;
    }

    public List<String> selectedColumns() {
        return List.copyOf(selectedColumns);
    }

    public Criterion criterion() {
        return criterion;
    }

    public Criterion havingCriterion() {
        return havingCriterion;
    }

    public List<String> groupColumns() {
        return List.copyOf(groupColumns);
    }

    public List<Order> orders() {
        return List.copyOf(orders);
    }

    public OptionalInt limit() {
        return limit == null ? OptionalInt.empty() : OptionalInt.of(limit);
    }

    public OptionalInt offset() {
        return offset == null ? OptionalInt.empty() : OptionalInt.of(offset);
    }

    public boolean isDistinct() {
        return distinct;
    }

    /** Selects only the given dynamic columns. Without this call, all table columns are selected. */
    public DynamicSelect columns(String... columnNames) {
        Objects.requireNonNull(columnNames, "columnNames");
        Arrays.stream(columnNames)
                .map(columnName -> requireName(columnName, "columnName"))
                .forEach(selectedColumns::add);
        return this;
    }

    /** Adds {@code DISTINCT} to the select. */
    public DynamicSelect distinct() {
        this.distinct = true;
        return this;
    }

    /** Replaces the current {@code WHERE} criterion. */
    public DynamicSelect where(Criterion criterion) {
        this.criterion = Objects.requireNonNull(criterion, "criterion");
        return this;
    }

    /** Adds an {@code AND} expression to the current {@code WHERE} clause. */
    public DynamicSelect and(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.and(criterion);
        return this;
    }

    /** Adds an {@code OR} expression to the current {@code WHERE} clause. */
    public DynamicSelect or(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.criterion = this.criterion == null ? criterion : this.criterion.or(criterion);
        return this;
    }

    /** Appends dynamic columns to the SQL {@code GROUP BY} clause. */
    public DynamicSelect groupBy(String... columnNames) {
        Objects.requireNonNull(columnNames, "columnNames");
        Arrays.stream(columnNames)
                .map(columnName -> requireName(columnName, "columnName"))
                .forEach(groupColumns::add);
        return this;
    }

    /** Replaces the current {@code HAVING} criterion. */
    public DynamicSelect having(Criterion criterion) {
        this.havingCriterion = Objects.requireNonNull(criterion, "criterion");
        return this;
    }

    /** Adds an {@code AND} expression to the current {@code HAVING} clause. */
    public DynamicSelect andHaving(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.havingCriterion = this.havingCriterion == null ? criterion : this.havingCriterion.and(criterion);
        return this;
    }

    /** Adds an {@code OR} expression to the current {@code HAVING} clause. */
    public DynamicSelect orHaving(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        this.havingCriterion = this.havingCriterion == null ? criterion : this.havingCriterion.or(criterion);
        return this;
    }

    /** Appends sort orders to this query. */
    public DynamicSelect orderBy(Order... orders) {
        Objects.requireNonNull(orders, "orders");
        Arrays.stream(orders).map(order -> Objects.requireNonNull(order, "order")).forEach(this.orders::add);
        return this;
    }

    /** Sets the maximum row count. */
    public DynamicSelect limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
        return this;
    }

    /** Sets the number of rows to skip before returning results. */
    public DynamicSelect offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        this.offset = offset;
        return this;
    }

    private static String requireName(String name, String label) {
        Objects.requireNonNull(name, label);
        if (name.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return name;
    }
}
