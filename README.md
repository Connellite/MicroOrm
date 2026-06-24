# MicroOrm

Lightweight annotation-driven JDBC ORM for Java 17+, built on [ExtraLib](https://github.com/connellite/ExtraLib).

**Status:** alpha (`1.0.0-alpha.1`) — core CRUD and multi-database support are stable; API may still evolve.

## Features

- Annotation mapping: `@Entity`, `@Column`, `@Id`, `@Transient`
- CRUD, batch insert, filtered select, streaming reads, custom `Query`
- Schema helpers: `createEntity`, `syncEntity` (add nullable columns), `dropEntity`
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
}

try (Connection connection = DriverManager.getConnection("jdbc:sqlite:app.db")) {
    MicroOrm orm = MicroOrm.sqlite(connection).register(User.class);
    orm.withSession(session -> {
        session.createEntity(User.class);

        User user = new User();
        user.name = "Ada";
        session.insertRow(user);

        User loaded = session.selectRow(User.class, user.id);
        return loaded;
    });
}
```

Use `MicroOrm.sqlite(dataSource)` (or `postgres`, `mysql`, `mssql`, `oracle`) when connections come from a pool. Prefer `orm.withSession(...)` so pooled connections are always released.

## Requirements

- Java 17+
- JDBC driver for your database
- Maven dependency on `io.github.connellite:ExtraLib:1.4`

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
- No relationships, migrations framework, or entity inheritance
- `Session` is not thread-safe — one session per thread
- Supported field types: numeric primitives/wrappers, `boolean`, `String`, `UUID`, `float`/`double`
- Numeric `0` is treated as unset for `@Id(autoIncrement = true)` inserts and PK lookups
- Entity inheritance is not supported (mapped superclass fields are rejected at registration)

## License

Apache License 2.0 — see [LICENSE](LICENSE).
