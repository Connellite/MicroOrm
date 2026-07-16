# MicroOrm

Lightweight annotation-driven JDBC ORM for Java 17+, built on [ExtraLib](https://github.com/connellite/ExtraLib).

**Status:** alpha  — core CRUD, relations, and multi-database support are stable; API may still evolve.

## Features

- Annotation mapping: `@Entity`, `@Column`, `@Id`, `@Transient`
- CRUD, batch insert, map-based filtered select, streaming reads, custom `Query`
- **`EntityQuery`**: fluent type-safe selects with `WHERE`, `ORDER BY`, `LIMIT`/`OFFSET`, and relation joins
- **Associations**: `@ManyToOne` / `@OneToMany` with lazy (`LazyRef`, `LazyCollection`) or eager (`EagerRef`, `EagerCollection`) loading
- Schema helpers: `createEntity`, `syncEntity` (add nullable columns), `dropEntity`
- **Dynamic tables**: runtime-defined schema and Map-based CRUD without entity classes
- Dialects: SQLite, PostgreSQL, MySQL, MSSQL, Oracle
- Spring-friendly: works with `TransactionAwareDataSourceProxy` without a compile-time Spring dependency
- Named parameters only — values are never concatenated into SQL

## Quick start

```java
@Entity(name = "users")
public class User {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    public User() {}

    public UUID getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }
}

try (Connection connection = DriverManager.getConnection("jdbc:sqlite:app.db")) {
    MicroOrm orm = MicroOrm.sqlite(connection).register(User.class);
    orm.withSession(session -> {
        session.createEntity(User.class);

        User user = new User();
        user.setName("Ada");
        session.insertRow(user);

        User loaded = session.selectRow(User.class, user.getId());
        return loaded;
    });
}
```

Use `MicroOrm.sqlite(dataSource)` (or `postgres`, `mysql`, `mssql`, `oracle`) when connections come from a pool. Prefer `orm.withSession(...)` so pooled connections are always released.

## EntityQuery

`EntityQuery` builds named-parameter SQL for a single root entity. Use it when you need composable predicates, sorting, pagination, or joins — without writing raw SQL.

```java
EntityQuery<User> query = EntityQuery.of(User.class)
        .where(EntityQuery.field("name").like("Ada%"))
        .and(EntityQuery.field("enabled").eq(true))
        .orderBy(EntityQuery.field("name").asc())
        .limit(20);

List<User> users = session.selectRows(query);
```

Join a relation and filter on joined fields with dotted paths:

```java
EntityQuery<Order> query = EntityQuery.of(Order.class)
        .leftJoin("customer")
        .where(EntityQuery.field("customer.name").eq("Acme"))
        .orderBy(EntityQuery.field("title").desc());

List<Order> orders = session.selectRows(query);
```

Supported join types: inner (default), left, right, full, and cross. For database-specific SQL or projections, use `Query` instead.

## Relations

Map associations with wrapper field types and `@JoinColumn` on the owning side:

```java
@Entity(name = "orders")
public class Order {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private LazyRef<Customer> customer;

    @OneToMany(mappedBy = "order")
    private LazyCollection<OrderItem> lines;
}

@Entity(name = "order_items")
public class OrderItem {
    @Id
    private long id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private LazyRef<Order> order;
}
```

- **`LazyRef` / `LazyCollection`** — load related rows on first `get()` while the session is open
- **`EagerRef` / `EagerCollection`** — materialize related rows when the owner is hydrated

For writes, attach entities with `LazyRef.to(entity)` or reference existing rows with `LazyRef.toId(Customer.class, id)`. Collections can be built with `LazyCollection.builder()` before insert/update.

Relation graphs (including cyclic references) are persisted through `session.insertRow` / `updateRow`.

## Dynamic tables

When entity classes are not needed, describe tables in code and use `DynamicSession`:

```java
DynamicTable docs = DynamicTable.builder("docs")
        .table("documents")
        .column("id", LogicalType.UUID, c -> c.primaryKey().notNull())
        .column("name", LogicalType.STRING, Column.Builder::notNull)
        .build();

orm.dynamicRegistry().register(docs);
orm.withDynamicSession(session -> {
    session.createTable("docs");
    session.insert("docs", Map.of("id", UUID.randomUUID(), "name", "alpha"));
    return session.select("docs", Map.of("name", "alpha"));
});
```

See `io.github.connellite.microorm.dynamic` in the Javadoc for details.

## Requirements

- Java 17+
- JDBC driver for your database
- Maven dependency on `io.github.connellite:ExtraLib:1.4`

## Install from JitPack

MicroOrm can be consumed from [JitPack](https://jitpack.io/) by adding the JitPack repository and depending on a Git tag, commit hash, or branch snapshot.

Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.connellite</groupId>
        <artifactId>MicroOrm</artifactId>
        <version>1.0.0-alpha.6</version>
    </dependency>
</dependencies>
```

Gradle:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.connellite:MicroOrm:1.0.0-alpha.2'
}
```

## Build and test

```bash
mvn test
mvn verify          # includes JaCoCo report in target/site/jacoco
mvn javadoc:javadoc # API docs in target/site/apidocs
```

Integration tests for PostgreSQL, MySQL, MSSQL, and Oracle use Testcontainers and require Docker.

## Java modules (JPMS)

MicroOrm ships as module `io.github.connellite.microorm`. In a modular application:

1. Open entity packages to ExtraLib (field access via VarHandle):

```java
module com.example.app {
    requires io.github.connellite.microorm;

    opens com.example.app.model to io.github.connellite.ExtraLib;
}
```

2. Allow ExtraLib to read your module (required for cross-module lookup):

```
--add-reads io.github.connellite.ExtraLib=com.example.app
```

Entity classes on the classpath (unnamed module) do not need the `add-reads` flag.

## Limitations (alpha)

- Single-column primary keys only (numeric or UUID)
- Associations: `@ManyToOne` and `@OneToMany` only (no `@OneToOne`, `@ManyToMany`, or embeddables)
- No migrations framework or entity inheritance
- `Session` is not thread-safe — one session per thread
- Supported field types: numeric primitives/wrappers, `boolean`, `String`, `UUID`, `float`/`double`
- Numeric `0` is treated as unset for `@Id(autoIncrement = true)` inserts and PK lookups
- Entity inheritance is not supported (mapped superclass fields are rejected at registration)
- `EntityQuery` covers one root entity with relation joins; no subqueries or projections

## License

Apache License 2.0 — see [LICENSE](LICENSE).
