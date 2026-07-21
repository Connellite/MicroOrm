package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.annotation.OneToMany;
import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.relation.LazyRef;
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

/**
 * Contract tests for {@link LazyRef} / {@link LazyCollection} insert, update, and delete.
 * Tables are created with explicit foreign-key constraints before each test.
 */
abstract class AbstractLazyRelationWriteTest {

    @Entity
    @Table(name = "write_documents")
    static class Document {
        @Id
        UUID id;

        @Column(nullable = false)
        String title;

        @OneToMany(mappedBy = "document")
        LazyCollection<StoredFile> files;

        Document() {
        }

        UUID getId() {
            return id;
        }

        String getTitle() {
            return title;
        }

        LazyCollection<StoredFile> getFiles() {
            return files;
        }
    }

    @Entity
    @Table(name = "write_files")
    static class StoredFile {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @ManyToOne
        @JoinColumn(name = "document_id", nullable = false)
        LazyRef<Document> document;

        StoredFile() {
        }

        UUID getId() {
            return id;
        }

        String getName() {
            return name;
        }

        LazyRef<Document> getDocument() {
            return document;
        }
    }

    /** Nullable back-link for cyclic graphs (two-pass insert). */
    @Entity
    @Table(name = "write_doc_heads")
    static class DocHead {
        @Id
        UUID id;

        @ManyToOne
        @JoinColumn(name = "primary_file_id", nullable = true)
        LazyRef<HeadFile> primaryFile;

        @OneToMany(mappedBy = "head")
        LazyCollection<HeadFile> files;

        DocHead() {
        }
    }

    @Entity
    @Table(name = "write_head_files")
    static class HeadFile {
        @Id
        UUID id;

        @Column(nullable = false)
        String label;

        @ManyToOne
        @JoinColumn(name = "head_id", nullable = true)
        LazyRef<DocHead> head;

        HeadFile() {
        }
    }

    private Connection connection;
    private MicroOrm orm;

    protected abstract Connection openConnection() throws SQLException;

    protected abstract MicroOrm createOrm(Connection connection);

    protected abstract RelationWriteFkSchema.Database databaseKind();

    @BeforeEach
    void setUp() throws SQLException {
        connection = openConnection();
        RelationWriteFkSchema.prepareConnection(databaseKind(), connection);
        RelationWriteFkSchema.recreateSchema(databaseKind(), connection);
        orm = createOrm(connection).register(Document.class, StoredFile.class, DocHead.class, HeadFile.class);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            RelationWriteFkSchema.dropSchema(databaseKind(), connection);
            connection.close();
        }
    }

    @Test
    void insertDocumentWithFilesBidirectional() throws SQLException {
        Document doc = new Document();
        doc.title = "Invoice";

        StoredFile a = new StoredFile();
        a.name = "page1.pdf";
        StoredFile b = new StoredFile();
        b.name = "page2.pdf";

        doc.files = LazyCollection.of(List.of(a, b));
        a.document = LazyRef.to(doc);
        b.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            assertNotNull(loaded);
            assertEquals("Invoice", loaded.getTitle());
            assertEquals(2, loaded.getFiles().get().size());
            assertEquals(List.of("page1.pdf", "page2.pdf"),
                    loaded.getFiles().get().stream().map(StoredFile::getName).sorted().toList());
            for (StoredFile file : loaded.getFiles().get()) {
                assertEquals(doc.id, file.getDocument().get().getId());
            }
        }
    }

    @Test
    void insertFileWithDocumentReference() throws SQLException {
        Document doc = new Document();
        doc.title = "Contract";
        StoredFile file = new StoredFile();
        file.name = "signed.pdf";
        file.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.insertRow(file);
        }

        try (Session session = orm.openSession()) {
            StoredFile loaded = session.selectRow(StoredFile.class, file.id);
            assertNotNull(loaded);
            assertEquals("signed.pdf", loaded.getName());
            assertEquals("Contract", loaded.getDocument().get().getTitle());
        }
    }

    @Test
    void updateDocumentReplacesFiles() throws SQLException {
        Document doc = new Document();
        doc.title = "Draft";
        StoredFile oldFile = new StoredFile();
        oldFile.name = "old.txt";
        doc.files = LazyCollection.of(List.of(oldFile));
        oldFile.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        StoredFile newFile = new StoredFile();
        newFile.name = "new.txt";
        doc.files = LazyCollection.of(List.of(newFile));
        newFile.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.updateRow(doc);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            List<StoredFile> files = loaded.getFiles().get();
            assertEquals(1, files.size());
            assertEquals("new.txt", files.get(0).getName());
        }
    }

    @Test
    void deleteDocumentRemovesFiles() throws SQLException {
        Document doc = new Document();
        doc.title = "Temp";
        StoredFile file = new StoredFile();
        file.name = "tmp.bin";
        doc.files = LazyCollection.of(List.of(file));
        file.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        try (Session session = orm.openSession()) {
            session.deleteRow(doc);
            assertNull(session.selectRow(Document.class, doc.id));
            assertNull(session.selectRow(StoredFile.class, file.id));
        }
    }

    @Test
    void insertCyclicNullableForeignKeys() throws SQLException {
        DocHead head = new DocHead();
        HeadFile file = new HeadFile();
        file.label = "cover";
        file.head = LazyRef.to(head);
        head.primaryFile = LazyRef.to(file);
        head.files = LazyCollection.of(List.of(file));

        try (Session session = orm.openSession()) {
            session.insertRow(head);
        }

        try (Session session = orm.openSession()) {
            DocHead loaded = session.selectRow(DocHead.class, head.id);
            assertNotNull(loaded);
            assertNotNull(loaded.primaryFile.get());
            assertEquals("cover", loaded.primaryFile.get().label);
            assertEquals(1, loaded.files.get().size());
            assertEquals(head.id, loaded.files.get().get(0).head.get().id);
        }
    }
}
