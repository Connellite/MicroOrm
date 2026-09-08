package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.CascadeType;
import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.JoinTable;
import io.github.connellite.microorm.annotation.ManyToMany;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.query.EntitySelect;
import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.session.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractManyToManyWriteTest {

    @Entity
    @Table(name = "m2m_authors")
    static class Author {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
        @JoinTable(
                name = "m2m_author_books",
                joinColumns = @JoinColumn(name = "author_id"),
                inverseJoinColumns = @JoinColumn(name = "book_id"))
        LazyCollection<Book> books;

        Author() {
        }
    }

    @Entity
    @Table(name = "m2m_books")
    static class Book {
        @Id
        UUID id;

        @Column(nullable = false)
        String title;

        @ManyToMany(mappedBy = "books")
        LazyCollection<Author> authors;

        Book() {
        }
    }

    @Entity
    @Table(name = "m2m_plain_authors")
    static class PlainAuthor {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @ManyToMany
        @JoinTable(
                name = "m2m_plain_author_books",
                joinColumns = @JoinColumn(name = "author_id"),
                inverseJoinColumns = @JoinColumn(name = "book_id"))
        LazyCollection<Book> books;

        PlainAuthor() {
        }
    }

    @Entity
    @Table(name = "m2m_remove_authors")
    static class RemoveAuthor {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @ManyToMany(cascade = CascadeType.ALL)
        @JoinTable(
                name = "m2m_remove_author_books",
                joinColumns = @JoinColumn(name = "author_id"),
                inverseJoinColumns = @JoinColumn(name = "book_id"))
        LazyCollection<RemoveBook> books;

        RemoveAuthor() {
        }
    }

    @Entity
    @Table(name = "m2m_remove_books")
    static class RemoveBook {
        @Id
        UUID id;

        @Column(nullable = false)
        String title;

        RemoveBook() {
        }
    }

    private Connection connection;
    private MicroOrm orm;

    protected abstract Connection openConnection() throws SQLException;

    protected abstract MicroOrm createOrm(Connection connection);

    @BeforeEach
    void setUp() throws SQLException {
        connection = openConnection();
        orm = createOrm(connection).register(
                Author.class, Book.class, PlainAuthor.class, RemoveAuthor.class, RemoveBook.class);
        try (Session session = orm.openSession()) {
            session.dropEntity(Author.class);
            session.dropEntity(PlainAuthor.class);
            session.dropEntity(Book.class);
            session.dropEntity(RemoveAuthor.class);
            session.dropEntity(RemoveBook.class);
            session.createEntity(Book.class);
            session.createEntity(Author.class);
            session.createEntity(PlainAuthor.class);
            session.createEntity(RemoveBook.class);
            session.createEntity(RemoveAuthor.class);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try {
            if (connection != null && !connection.isClosed() && orm != null) {
                try (Session session = orm.openSession()) {
                    session.dropEntity(Author.class);
                    session.dropEntity(PlainAuthor.class);
                    session.dropEntity(Book.class);
                    session.dropEntity(RemoveAuthor.class);
                    session.dropEntity(RemoveBook.class);
                }
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    void insertAuthorPersistsBooksAndJoinRows() throws SQLException {
        Author author = new Author();
        author.name = "Ada";
        Book first = new Book();
        first.title = "Notes";
        Book second = new Book();
        second.title = "Sketch";
        author.books = LazyCollection.of(List.of(first, second));

        try (Session session = orm.openSession()) {
            session.insertRow(author);
        }

        try (Session session = orm.openSession()) {
            Author loaded = session.selectRow(Author.class, author.id);
            assertEquals("Ada", loaded.name);
            assertEquals(List.of("Notes", "Sketch"),
                    loaded.books.get().stream().map(book -> book.title).sorted().toList());
            Book loadedBook = session.selectRow(Book.class, first.id);
            assertEquals(1, loadedBook.authors.get().size());
            assertEquals(author.id, loadedBook.authors.get().get(0).id);
        }
    }

    @Test
    void insertAuthorWithAssignedUuids() throws SQLException {
        UUID authorId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID bookId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        Author author = new Author();
        author.id = authorId;
        author.name = "Grace";
        Book book = new Book();
        book.id = bookId;
        book.title = "Cobol";
        author.books = LazyCollection.of(List.of(book));

        try (Session session = orm.openSession()) {
            session.insertRow(author);
        }

        try (Session session = orm.openSession()) {
            assertEquals(bookId, session.selectRow(Author.class, authorId).books.get().get(0).id);
            assertEquals(authorId, session.selectRow(Book.class, bookId).authors.get().get(0).id);
        }
    }

    @Test
    void insertExistingBooksByReferenceWritesJoinRowsOnly() throws SQLException {
        Book book = new Book();
        book.id = UUID.fromString("33333333-3333-4333-8333-333333333333");
        book.title = "Standalone";
        try (Session session = orm.openSession()) {
            session.insertRow(book);
        }

        Author author = new Author();
        author.id = UUID.fromString("44444444-4444-4444-8444-444444444444");
        author.name = "Linker";
        author.books = LazyCollection.of(List.of(book));
        try (Session session = orm.openSession()) {
            session.insertRow(author);
        }

        try (Session session = orm.openSession()) {
            assertEquals(1, session.selectRow(Author.class, author.id).books.get().size());
            assertEquals("Standalone", session.selectRow(Author.class, author.id).books.get().get(0).title);
        }
    }

    @Test
    void inverseInsertDoesNotWriteJoinRows() throws SQLException {
        Author author = new Author();
        author.id = UUID.fromString("55555555-5555-4555-8555-555555555555");
        author.name = "Inverse";
        try (Session session = orm.openSession()) {
            session.insertRow(author);
        }
        Book book = new Book();
        book.id = UUID.fromString("66666666-6666-4666-8666-666666666666");
        book.title = "Owned elsewhere";
        book.authors = LazyCollection.of(List.of(author));

        try (Session session = orm.openSession()) {
            session.insertRow(book);
        }

        try (Session session = orm.openSession()) {
            assertNotNull(session.selectRow(Book.class, book.id));
            assertNotNull(session.selectRow(Author.class, author.id));
            assertEquals(0, session.selectRow(Book.class, book.id).authors.get().size());
            assertEquals(0, session.selectRow(Author.class, author.id).books.get().size());
        }
    }

    @Test
    void updateAuthorReplacesJoinRows() throws SQLException {
        Author author = new Author();
        author.name = "Editor";
        Book oldBook = new Book();
        oldBook.title = "Draft";
        author.books = LazyCollection.of(List.of(oldBook));
        try (Session session = orm.openSession()) {
            session.insertRow(author);
        }

        Book newBook = new Book();
        newBook.title = "Final";
        author.books = LazyCollection.of(List.of(newBook));
        try (Session session = orm.openSession()) {
            session.updateRow(author);
        }

        try (Session session = orm.openSession()) {
            List<Book> books = session.selectRow(Author.class, author.id).books.get();
            assertEquals(1, books.size());
            assertEquals("Final", books.get(0).title);
            assertNotNull(session.selectRow(Book.class, oldBook.id));
        }
    }

    @Test
    void deleteAuthorRemovesJoinRowsButKeepsBooks() throws SQLException {
        Author author = new Author();
        author.name = "Temp";
        Book book = new Book();
        book.title = "Keep";
        author.books = LazyCollection.of(List.of(book));
        try (Session session = orm.openSession()) {
            session.insertRow(author);
        }

        try (Session session = orm.openSession()) {
            session.deleteRow(author);
            assertNull(session.selectRow(Author.class, author.id));
            assertNotNull(session.selectRow(Book.class, book.id));
            assertEquals(0, session.selectRow(Book.class, book.id).authors.get().size());
        }
    }

    @Test
    void deleteAuthorWithRemoveCascadeDeletesBooks() throws SQLException {
        RemoveAuthor author = new RemoveAuthor();
        author.name = "Cascade";
        RemoveBook book = new RemoveBook();
        book.title = "Gone";
        author.books = LazyCollection.of(List.of(book));
        try (Session session = orm.openSession()) {
            session.insertRow(author);
        }

        try (Session session = orm.openSession()) {
            session.deleteRow(author);
            assertNull(session.selectRow(RemoveAuthor.class, author.id));
            assertNull(session.selectRow(RemoveBook.class, book.id));
        }
    }

    @Test
    void persistRejectsTransientBookWithoutCascade() throws SQLException {
        PlainAuthor author = new PlainAuthor();
        author.id = UUID.fromString("77777777-7777-4777-8777-777777777777");
        author.name = "No cascade";
        Book book = new Book();
        book.title = "Missing";
        author.books = LazyCollection.of(List.of(book));

        try (Session session = orm.openSession()) {
            var error = assertThrows(
                    io.github.connellite.microorm.exception.MicroOrmException.class,
                    () -> session.insertRow(author));
            assertTrue(error.getMessage().contains("transient instance must be saved"));
            assertNull(session.selectRow(PlainAuthor.class, author.id));
        }
    }

    @Test
    void persistRejectsAssignedIdBookWithoutCascadeWhenTargetRowIsMissing() throws SQLException {
        PlainAuthor author = new PlainAuthor();
        author.id = UUID.fromString("77777777-7777-4777-8777-777777777778");
        author.name = "No cascade assigned";
        Book book = new Book();
        book.id = UUID.fromString("77777777-7777-4777-8777-777777777779");
        book.title = "Missing assigned";
        author.books = LazyCollection.of(List.of(book));

        try (Session session = orm.openSession()) {
            var error = assertThrows(
                    io.github.connellite.microorm.exception.MicroOrmException.class,
                    () -> session.insertRow(author));
            assertTrue(error.getMessage().contains("transient instance must be saved"));
            assertNull(session.selectRow(PlainAuthor.class, author.id));
            assertNull(session.selectRow(Book.class, book.id));
        }
    }

    @Test
    void joinQueryFindsAuthorByBookTitle() throws SQLException {
        Author author = new Author();
        author.name = "Joiner";
        Book book = new Book();
        book.title = "Queryable";
        author.books = LazyCollection.of(List.of(book));
        try (Session session = orm.openSession()) {
            session.insertRow(author);
        }

        try (Session session = orm.openSession()) {
            List<Author> found = session.selectRows(EntitySelect.of(Author.class)
                    .join("books")
                    .where(EntitySelect.field("books.title").eq("Queryable")));
            assertEquals(1, found.size());
            assertEquals(author.id, found.get(0).id);
        }
    }
}
