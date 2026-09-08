package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.CascadeType;
import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.annotation.OneToMany;
import io.github.connellite.microorm.exception.MicroOrmException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link LazyRef} / {@link LazyCollection} insert, update, and delete.
 * Tables are created with explicit foreign-key constraints before each test.
 */
abstract class AbstractLazyRelationWriteTest {

    @Entity
    @Table(name = "write_folders")
    static class Folder {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @Column(name = "item_count", nullable = false)
        int itemCount;

        @OneToMany(mappedBy = "folder")
        LazyCollection<Document> documents;

        Folder() {
        }
    }

    @Entity
    @Table(name = "write_documents")
    static class Document {
        @Id
        UUID id;

        @Column(nullable = false)
        String title;

        @ManyToOne
        @JoinColumn(name = "folder_id")
        LazyRef<Folder> folder;

        @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
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

        @ManyToOne(cascade = CascadeType.PERSIST)
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

        @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
        @JoinColumn(name = "primary_file_id", nullable = true)
        LazyRef<HeadFile> primaryFile;

        @OneToMany(mappedBy = "head", cascade = CascadeType.ALL, orphanRemoval = true)
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

        @ManyToOne(cascade = CascadeType.PERSIST)
        @JoinColumn(name = "head_id", nullable = true)
        LazyRef<DocHead> head;

        HeadFile() {
        }
    }

    /** Required many-to-one ({@code optional=false}) without cascade — Hibernate rejects a transient target. */
    @Entity
    @Table(name = "write_required_docs")
    static class RequiredDocument {
        @Id
        UUID id;

        @Column(nullable = false)
        String title;

        @ManyToOne(optional = false)
        @JoinColumn(name = "folder_id")
        LazyRef<Folder> folder;

        RequiredDocument() {
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
        orm = createOrm(connection).register(
                Folder.class, Document.class, StoredFile.class, DocHead.class, HeadFile.class, RequiredDocument.class);
        try (Session session = orm.openSession()) {
            session.dropEntity(RequiredDocument.class);
            session.createEntity(RequiredDocument.class);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try {
            if (connection != null && !connection.isClosed() && orm != null) {
                try (Session session = orm.openSession()) {
                    session.dropEntity(RequiredDocument.class);
                }
                RelationWriteFkSchema.dropSchema(databaseKind(), connection);
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
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
    void insertDocumentWithAssignedUuids() throws SQLException {
        UUID documentId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID fileAId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID fileBId = UUID.fromString("33333333-3333-4333-8333-333333333333");

        Document doc = new Document();
        doc.id = documentId;
        doc.title = "Migrated invoice";

        StoredFile a = new StoredFile();
        a.id = fileAId;
        a.name = "page1.pdf";
        StoredFile b = new StoredFile();
        b.id = fileBId;
        b.name = "page2.pdf";

        doc.files = LazyCollection.of(List.of(a, b));
        a.document = LazyRef.to(doc);
        b.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, documentId);
            assertEquals(documentId, loaded.getId());
            assertEquals(2, loaded.getFiles().get().size());
            assertEquals(fileAId, session.selectRow(StoredFile.class, fileAId).getId());
            assertEquals(fileBId, session.selectRow(StoredFile.class, fileBId).getId());
        }
    }

    @Test
    void insertFileWithAssignedUuidsAndNewDocument() throws SQLException {
        UUID documentId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        UUID fileId = UUID.fromString("55555555-5555-4555-8555-555555555555");

        Document doc = new Document();
        doc.id = documentId;
        doc.title = "Migrated contract";
        StoredFile file = new StoredFile();
        file.id = fileId;
        file.name = "signed.pdf";
        file.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.insertRow(file);
        }

        try (Session session = orm.openSession()) {
            StoredFile loaded = session.selectRow(StoredFile.class, fileId);
            assertEquals(fileId, loaded.getId());
            assertEquals(documentId, loaded.getDocument().get().getId());
            assertEquals("Migrated contract", loaded.getDocument().get().getTitle());
        }
    }

    @Test
    void insertFileWithAssignedUuidReferencesExistingDocument() throws SQLException {
        UUID documentId = UUID.fromString("66666666-6666-4666-8666-666666666666");
        UUID fileId = UUID.fromString("77777777-7777-4777-8777-777777777777");

        Document doc = new Document();
        doc.id = documentId;
        doc.title = "Already stored";

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        StoredFile file = new StoredFile();
        file.id = fileId;
        file.name = "attachment.pdf";
        file.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.insertRow(file);
        }

        try (Session session = orm.openSession()) {
            StoredFile loaded = session.selectRow(StoredFile.class, fileId);
            assertEquals(documentId, loaded.getDocument().get().getId());
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

    @Test
    void insertCyclicAssignedUuids() throws SQLException {
        UUID headId = UUID.fromString("88888888-8888-4888-8888-888888888888");
        UUID fileId = UUID.fromString("99999999-9999-4999-8999-999999999999");

        DocHead head = new DocHead();
        head.id = headId;
        HeadFile file = new HeadFile();
        file.id = fileId;
        file.label = "cover";
        file.head = LazyRef.to(head);
        head.primaryFile = LazyRef.to(file);
        head.files = LazyCollection.of(List.of(file));

        try (Session session = orm.openSession()) {
            session.insertRow(head);
        }

        try (Session session = orm.openSession()) {
            DocHead loaded = session.selectRow(DocHead.class, headId);
            assertEquals(fileId, loaded.primaryFile.get().id);
            assertEquals(headId, loaded.files.get().get(0).head.get().id);
        }
    }

    @Test
    void insertDocumentLeavesExistingFolderUntouched() throws SQLException {
        Folder folder = persistFolder("Inbox", 3);
        int folderCountBefore = folder.itemCount;
        folder.itemCount = 99;
        folder.name = "should not persist";

        Document doc = new Document();
        doc.id = UUID.fromString("aaaaaaa1-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
        doc.title = "New invoice";
        doc.folder = LazyRef.to(folder);
        StoredFile file = new StoredFile();
        file.id = UUID.fromString("aaaaaaa2-aaaa-4aaa-8aaa-aaaaaaaaaaa2");
        file.name = "scan.pdf";
        doc.files = LazyCollection.of(List.of(file));
        file.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        try (Session session = orm.openSession()) {
            Folder loadedFolder = session.selectRow(Folder.class, folder.id);
            assertEquals("Inbox", loadedFolder.name);
            assertEquals(folderCountBefore, loadedFolder.itemCount);
            assertEquals(1, loadedFolder.documents.get().size());
            Document loadedDoc = session.selectRow(Document.class, doc.id);
            assertEquals(folder.id, loadedDoc.folder.get().id);
            assertEquals("scan.pdf", loadedDoc.files.get().get(0).name);
        }
    }

    @Test
    void insertDocumentByFolderIdDoesNotLoadOrUpdateFolder() throws SQLException {
        Folder folder = persistFolder("Archive", 5);

        Document doc = new Document();
        doc.id = UUID.fromString("bbbbbbb1-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
        doc.title = "Archived note";
        doc.folder = LazyRef.toId(Folder.class, folder.id);

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        try (Session session = orm.openSession()) {
            Folder loadedFolder = session.selectRow(Folder.class, folder.id);
            assertEquals("Archive", loadedFolder.name);
            assertEquals(5, loadedFolder.itemCount);
            Document loadedDoc = session.selectRow(Document.class, doc.id);
            assertEquals(folder.id, loadedDoc.folder.get().id);
        }
    }

    @Test
    void updateDocumentMovesToAnotherFolder() throws SQLException {
        Folder inbox = persistFolder("Inbox", 1);
        Folder archive = persistFolder("Archive", 2);

        Document doc = new Document();
        doc.id = UUID.fromString("ccccccc0-cccc-4ccc-8ccc-ccccccccccc0");
        doc.title = "Note";
        doc.folder = LazyRef.toId(Folder.class, inbox.id);
        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        doc.folder = LazyRef.toId(Folder.class, archive.id);
        try (Session session = orm.openSession()) {
            session.updateRow(doc);
        }

        try (Session session = orm.openSession()) {
            assertEquals(archive.id, session.selectRow(Document.class, doc.id).folder.get().id);
            assertEquals("Inbox", session.selectRow(Folder.class, inbox.id).name);
            assertEquals("Archive", session.selectRow(Folder.class, archive.id).name);
        }
    }

    @Test
    void updateDocumentClearsFolder() throws SQLException {
        Folder folder = persistFolder("Inbox", 1);
        Document doc = new Document();
        doc.id = UUID.fromString("ccccccc2-cccc-4ccc-8ccc-ccccccccccc2");
        doc.title = "Loose note";
        doc.folder = LazyRef.toId(Folder.class, folder.id);
        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        doc.folder = null;
        try (Session session = orm.openSession()) {
            session.updateRow(doc);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            assertTrue(loaded.folder.isNull());
            assertNull(loaded.folder.get());
            assertEquals("Inbox", session.selectRow(Folder.class, folder.id).name);
        }
    }

    @Test
    void incrementFolderCounterExplicitlyWhenInsertingDocument() throws SQLException {
        Folder folder = persistFolder("Projects", 1);

        Document doc = new Document();
        doc.id = UUID.fromString("ccccccc1-cccc-4ccc-8ccc-ccccccccccc1");
        doc.title = "Spec";
        doc.folder = LazyRef.toId(Folder.class, folder.id);

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
            Folder toUpdate = session.selectRow(Folder.class, folder.id);
            toUpdate.itemCount = toUpdate.itemCount + 1;
            session.updateRow(toUpdate);
        }

        try (Session session = orm.openSession()) {
            assertEquals(2, session.selectRow(Folder.class, folder.id).itemCount);
            assertEquals("Projects", session.selectRow(Folder.class, folder.id).name);
            assertEquals(folder.id, session.selectRow(Document.class, doc.id).folder.get().id);
        }
    }

    @Test
    void updateLoadedFileDoesNotChangeDocumentOrFolder() throws SQLException {
        Folder folder = persistFolder("Shared", 1);
        Document doc = new Document();
        doc.id = UUID.fromString("ddddddd1-dddd-4ddd-8ddd-ddddddddddd1");
        doc.title = "Report";
        doc.folder = LazyRef.to(folder);
        StoredFile file = new StoredFile();
        file.id = UUID.fromString("ddddddd2-dddd-4ddd-8ddd-ddddddddddd2");
        file.name = "v1.pdf";
        doc.files = LazyCollection.of(List.of(file));
        file.document = LazyRef.to(doc);
        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            StoredFile loadedFile = loaded.getFiles().get().get(0);
            loadedFile.name = "v2.pdf";
            session.updateRow(loadedFile);
        }

        try (Session session = orm.openSession()) {
            assertEquals("v2.pdf", session.selectRow(StoredFile.class, file.id).name);
            assertEquals("Report", session.selectRow(Document.class, doc.id).title);
            Folder loadedFolder = session.selectRow(Folder.class, folder.id);
            assertEquals("Shared", loadedFolder.name);
            assertEquals(1, loadedFolder.itemCount);
        }
    }

    @Test
    void updateDocumentTitleWithoutLoadingFilesKeepsFiles() throws SQLException {
        Document doc = new Document();
        doc.title = "Draft";
        StoredFile file = new StoredFile();
        file.name = "keep.pdf";
        doc.files = LazyCollection.of(List.of(file));
        file.document = LazyRef.to(doc);
        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            loaded.title = "Final";
            session.updateRow(loaded);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            assertEquals("Final", loaded.title);
            assertEquals(1, loaded.getFiles().get().size());
            assertEquals("keep.pdf", loaded.getFiles().get().get(0).name);
        }
    }

    @Test
    void updateDocumentWithLoadedFilesDoesNotDuplicateThem() throws SQLException {
        Document doc = new Document();
        doc.title = "Bundle";
        StoredFile file = new StoredFile();
        file.name = "page.pdf";
        doc.files = LazyCollection.of(List.of(file));
        file.document = LazyRef.to(doc);
        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            loaded.getFiles().get();
            loaded.title = "Bundle v2";
            session.updateRow(loaded);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            assertEquals("Bundle v2", loaded.title);
            assertEquals(1, loaded.getFiles().get().size());
            assertEquals("page.pdf", loaded.getFiles().get().get(0).name);
        }
    }

    @Test
    void updateLoadedDocumentAlsoUpdatesLoadedFileName() throws SQLException {
        Document doc = new Document();
        doc.title = "Contract";
        StoredFile file = new StoredFile();
        file.name = "old.pdf";
        doc.files = LazyCollection.of(List.of(file));
        file.document = LazyRef.to(doc);
        try (Session session = orm.openSession()) {
            session.insertRow(doc);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            StoredFile loadedFile = loaded.getFiles().get().get(0);
            loaded.title = "Signed contract";
            loadedFile.name = "signed.pdf";
            session.updateRow(loaded);
        }

        try (Session session = orm.openSession()) {
            Document loaded = session.selectRow(Document.class, doc.id);
            assertEquals("Signed contract", loaded.title);
            assertEquals("signed.pdf", loaded.getFiles().get().get(0).name);
        }
    }

    @Test
    void persistNullableTransientManyToOneWithoutCascadeWritesNullFk() throws SQLException {
        Folder folder = new Folder();
        folder.name = "Missing";
        folder.itemCount = 0;

        Document doc = new Document();
        doc.id = UUID.fromString("eeeeeee2-eeee-4eee-8eee-eeeeeeeeeee2");
        doc.title = "Orphaned";
        doc.folder = LazyRef.to(folder);

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
            Document loaded = session.selectRow(Document.class, doc.id);
            assertEquals("Orphaned", loaded.title);
            assertNull(loaded.folder.get());
            assertNull(folder.id);
        }
    }

    @Test
    void persistRejectsRequiredTransientManyToOneWithoutCascade() throws SQLException {
        Folder folder = new Folder();
        folder.name = "Missing";
        folder.itemCount = 0;

        RequiredDocument doc = new RequiredDocument();
        doc.id = UUID.fromString("eeeeeee3-eeee-4eee-8eee-eeeeeeeeeee3");
        doc.title = "Required folder";
        doc.folder = LazyRef.to(folder);

        try (Session session = orm.openSession()) {
            MicroOrmException error = assertThrows(MicroOrmException.class, () -> session.insertRow(doc));
            assertTrue(error.getMessage().contains("transient instance must be saved"));
            assertNull(session.selectRow(RequiredDocument.class, doc.id));
        }
    }

    @Test
    void persistDoesNotCascadeOneToManyWithoutCascade() throws SQLException {
        Folder folder = new Folder();
        folder.id = UUID.fromString("fffffff0-ffff-4fff-8fff-fffffffffff0");
        folder.name = "Empty";
        folder.itemCount = 0;
        Document doc = new Document();
        doc.id = UUID.fromString("fffffff1-ffff-4fff-8fff-fffffffffff1");
        doc.title = "Not cascaded";
        doc.folder = LazyRef.to(folder);
        folder.documents = LazyCollection.of(List.of(doc));

        try (Session session = orm.openSession()) {
            session.insertRow(folder);
        }

        try (Session session = orm.openSession()) {
            assertNotNull(session.selectRow(Folder.class, folder.id));
            assertNull(session.selectRow(Document.class, doc.id));
            assertEquals(0, session.selectRow(Folder.class, folder.id).documents.get().size());
        }
    }

    @Test
    void persistNullableAssignedIdManyToOneWithoutCascadeWritesNullFkWhenTargetRowIsMissing() throws SQLException {
        Folder folder = new Folder();
        folder.id = UUID.fromString("fffffff2-ffff-4fff-8fff-fffffffffff2");
        folder.name = "Missing assigned";
        folder.itemCount = 0;

        Document doc = new Document();
        doc.id = UUID.fromString("fffffff3-ffff-4fff-8fff-fffffffffff3");
        doc.title = "Nullable assigned target";
        doc.folder = LazyRef.to(folder);

        try (Session session = orm.openSession()) {
            session.insertRow(doc);
            assertNotNull(session.selectRow(Document.class, doc.id));
            assertNull(session.selectRow(Document.class, doc.id).folder.get());
            assertNull(session.selectRow(Folder.class, folder.id));
        }
    }

    @Test
    void persistRejectsRequiredAssignedIdManyToOneWithoutCascadeWhenTargetRowIsMissing() throws SQLException {
        Folder folder = new Folder();
        folder.id = UUID.fromString("fffffff4-ffff-4fff-8fff-fffffffffff4");
        folder.name = "Missing assigned";
        folder.itemCount = 0;

        RequiredDocument doc = new RequiredDocument();
        doc.id = UUID.fromString("fffffff5-ffff-4fff-8fff-fffffffffff5");
        doc.title = "Required assigned target";
        doc.folder = LazyRef.to(folder);

        try (Session session = orm.openSession()) {
            MicroOrmException error = assertThrows(MicroOrmException.class, () -> session.insertRow(doc));
            assertTrue(error.getMessage().contains("transient instance must be saved"));
            assertNull(session.selectRow(RequiredDocument.class, doc.id));
        }
    }

    @Test
    void updateWithPersistOnlyCascadeDoesNotPersistNewManyToOneTarget() throws SQLException {
        Document doc = new Document();
        doc.id = UUID.fromString("fffffff6-ffff-4fff-8fff-fffffffffff6");
        doc.title = "Original";
        StoredFile file = new StoredFile();
        file.id = UUID.fromString("fffffff7-ffff-4fff-8fff-fffffffffff7");
        file.name = "file.txt";
        file.document = LazyRef.to(doc);

        try (Session session = orm.openSession()) {
            session.insertRow(file);
        }

        Document missing = new Document();
        missing.id = UUID.fromString("fffffff8-ffff-4fff-8fff-fffffffffff8");
        missing.title = "Missing";
        file.document = LazyRef.to(missing);

        try (Session session = orm.openSession()) {
            MicroOrmException error = assertThrows(MicroOrmException.class, () -> session.updateRow(file));
            assertTrue(error.getMessage().contains("transient instance must be saved"));
            assertNull(session.selectRow(Document.class, missing.id));
            assertEquals(doc.id, session.selectRow(StoredFile.class, file.id).document.get().id);
        }
    }

    private Folder persistFolder(String name, int itemCount) throws SQLException {
        Folder folder = new Folder();
        folder.id = UUID.randomUUID();
        folder.name = name;
        folder.itemCount = itemCount;
        try (Session session = orm.openSession()) {
            session.insertRow(folder);
        }
        return folder;
    }
}
