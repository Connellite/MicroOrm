package io.github.connellite.microorm;

import io.github.connellite.microorm.annotation.CascadeType;
import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.OneToOne;
import io.github.connellite.microorm.annotation.Table;
import io.github.connellite.microorm.query.EntitySelect;
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

abstract class AbstractOneToOneWriteTest {

    @Entity
    @Table(name = "o2o_users")
    static class User {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @OneToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
        @JoinColumn(name = "profile_id")
        LazyRef<Profile> profile;

        User() {
        }
    }

    @Entity
    @Table(name = "o2o_profiles")
    static class Profile {
        @Id
        UUID id;

        @Column(nullable = false)
        String bio;

        @OneToOne(mappedBy = "profile")
        LazyRef<User> user;

        Profile() {
        }
    }

    @Entity
    @Table(name = "o2o_plain_users")
    static class PlainUser {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @OneToOne
        @JoinColumn(name = "profile_id")
        LazyRef<Profile> profile;

        PlainUser() {
        }
    }

    @Entity
    @Table(name = "o2o_required_users")
    static class RequiredUser {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @OneToOne(optional = false)
        @JoinColumn(name = "profile_id")
        LazyRef<Profile> profile;

        RequiredUser() {
        }
    }

    @Entity
    @Table(name = "o2o_remove_users")
    static class RemoveUser {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
        @JoinColumn(name = "profile_id")
        LazyRef<RemoveProfile> profile;

        RemoveUser() {
        }
    }

    @Entity
    @Table(name = "o2o_remove_profiles")
    static class RemoveProfile {
        @Id
        UUID id;

        @Column(nullable = false)
        String bio;

        RemoveProfile() {
        }
    }

    @Entity
    @Table(name = "o2o_employees")
    static class Employee {
        @Id
        UUID id;

        @Column(nullable = false)
        String name;

        @OneToOne(mappedBy = "employee", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
        LazyRef<Desk> desk;

        Employee() {
        }
    }

    @Entity
    @Table(name = "o2o_desks")
    static class Desk {
        @Id
        UUID id;

        @Column(nullable = false)
        String code;

        @OneToOne
        @JoinColumn(name = "employee_id")
        LazyRef<Employee> employee;

        Desk() {
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
                User.class, Profile.class, PlainUser.class, RequiredUser.class,
                RemoveUser.class, RemoveProfile.class,
                Employee.class, Desk.class);
        try (Session session = orm.openSession()) {
            session.dropEntity(User.class);
            session.dropEntity(PlainUser.class);
            session.dropEntity(RequiredUser.class);
            session.dropEntity(RemoveUser.class);
            session.dropEntity(Employee.class);
            session.dropEntity(Profile.class);
            session.dropEntity(RemoveProfile.class);
            session.dropEntity(Desk.class);
            session.createEntity(Profile.class);
            session.createEntity(RemoveProfile.class);
            session.createEntity(Desk.class);
            session.createEntity(User.class);
            session.createEntity(PlainUser.class);
            session.createEntity(RequiredUser.class);
            session.createEntity(RemoveUser.class);
            session.createEntity(Employee.class);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try {
            if (connection != null && !connection.isClosed() && orm != null) {
                try (Session session = orm.openSession()) {
                    session.dropEntity(User.class);
                    session.dropEntity(PlainUser.class);
                    session.dropEntity(RequiredUser.class);
                    session.dropEntity(RemoveUser.class);
                    session.dropEntity(Employee.class);
                    session.dropEntity(Profile.class);
                    session.dropEntity(RemoveProfile.class);
                    session.dropEntity(Desk.class);
                }
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    void insertUserPersistsProfileAndForeignKey() throws SQLException {
        User user = new User();
        user.name = "Ada";
        Profile profile = new Profile();
        profile.bio = "Notes";
        user.profile = LazyRef.to(profile);

        try (Session session = orm.openSession()) {
            session.insertRow(user);
        }

        try (Session session = orm.openSession()) {
            User loaded = session.selectRow(User.class, user.id);
            assertEquals("Ada", loaded.name);
            assertNotNull(loaded.profile.get());
            assertEquals("Notes", loaded.profile.get().bio);
            Profile loadedProfile = session.selectRow(Profile.class, profile.id);
            assertEquals(user.id, loadedProfile.user.get().id);
        }
    }

    @Test
    void insertUserWithAssignedUuids() throws SQLException {
        UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID profileId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        User user = new User();
        user.id = userId;
        user.name = "Grace";
        Profile profile = new Profile();
        profile.id = profileId;
        profile.bio = "Cobol";
        user.profile = LazyRef.to(profile);

        try (Session session = orm.openSession()) {
            session.insertRow(user);
        }

        try (Session session = orm.openSession()) {
            assertEquals(profileId, session.selectRow(User.class, userId).profile.get().id);
            assertEquals(userId, session.selectRow(Profile.class, profileId).user.get().id);
        }
    }

    @Test
    void insertExistingProfileByReferenceWritesForeignKeyOnly() throws SQLException {
        Profile profile = new Profile();
        profile.id = UUID.fromString("33333333-3333-4333-8333-333333333333");
        profile.bio = "Standalone";
        try (Session session = orm.openSession()) {
            session.insertRow(profile);
        }

        User user = new User();
        user.id = UUID.fromString("44444444-4444-4444-8444-444444444444");
        user.name = "Linker";
        user.profile = LazyRef.to(profile);
        try (Session session = orm.openSession()) {
            session.insertRow(user);
        }

        try (Session session = orm.openSession()) {
            assertEquals("Standalone", session.selectRow(User.class, user.id).profile.get().bio);
            assertEquals(1, session.selectRows(Profile.class).size());
        }
    }

    @Test
    void inverseInsertDoesNotWriteOwningForeignKey() throws SQLException {
        User user = new User();
        user.id = UUID.fromString("55555555-5555-4555-8555-555555555555");
        user.name = "Inverse";
        try (Session session = orm.openSession()) {
            session.insertRow(user);
        }
        Profile profile = new Profile();
        profile.id = UUID.fromString("66666666-6666-4666-8666-666666666666");
        profile.bio = "Owned elsewhere";
        profile.user = LazyRef.to(user);

        try (Session session = orm.openSession()) {
            session.insertRow(profile);
        }

        try (Session session = orm.openSession()) {
            assertNotNull(session.selectRow(Profile.class, profile.id));
            assertNull(session.selectRow(User.class, user.id).profile.get());
        }
    }

    @Test
    void persistInverseSideWritesOwningForeignKey() throws SQLException {
        Employee employee = new Employee();
        employee.name = "Worker";
        Desk desk = new Desk();
        desk.code = "A-1";
        employee.desk = LazyRef.to(desk);

        try (Session session = orm.openSession()) {
            session.insertRow(employee);
        }

        try (Session session = orm.openSession()) {
            Employee loaded = session.selectRow(Employee.class, employee.id);
            assertEquals("A-1", loaded.desk.get().code);
            assertEquals(employee.id, session.selectRow(Desk.class, desk.id).employee.get().id);
        }
    }

    @Test
    void updateUserReplacesProfileWithoutDeletingOld() throws SQLException {
        User user = new User();
        user.name = "Editor";
        Profile oldProfile = new Profile();
        oldProfile.bio = "Draft";
        user.profile = LazyRef.to(oldProfile);
        try (Session session = orm.openSession()) {
            session.insertRow(user);
        }

        Profile newProfile = new Profile();
        newProfile.bio = "Final";
        user.profile = LazyRef.to(newProfile);
        try (Session session = orm.openSession()) {
            session.updateRow(user);
        }

        try (Session session = orm.openSession()) {
            assertEquals("Final", session.selectRow(User.class, user.id).profile.get().bio);
            assertNotNull(session.selectRow(Profile.class, oldProfile.id));
        }
    }

    @Test
    void orphanRemovalDeletesReplacedOwningTarget() throws SQLException {
        RemoveUser user = new RemoveUser();
        user.name = "Temp";
        RemoveProfile oldProfile = new RemoveProfile();
        oldProfile.bio = "Old";
        user.profile = LazyRef.to(oldProfile);
        try (Session session = orm.openSession()) {
            session.insertRow(user);
        }

        RemoveProfile newProfile = new RemoveProfile();
        newProfile.bio = "New";
        user.profile = LazyRef.to(newProfile);
        try (Session session = orm.openSession()) {
            session.updateRow(user);
        }

        try (Session session = orm.openSession()) {
            assertEquals("New", session.selectRow(RemoveUser.class, user.id).profile.get().bio);
            assertNull(session.selectRow(RemoveProfile.class, oldProfile.id));
        }
    }

    @Test
    void orphanRemovalDeletesReplacedInverseTarget() throws SQLException {
        Employee employee = new Employee();
        employee.name = "Mover";
        Desk oldDesk = new Desk();
        oldDesk.code = "B-1";
        employee.desk = LazyRef.to(oldDesk);
        try (Session session = orm.openSession()) {
            session.insertRow(employee);
        }

        Desk newDesk = new Desk();
        newDesk.code = "B-2";
        employee.desk = LazyRef.to(newDesk);
        try (Session session = orm.openSession()) {
            session.updateRow(employee);
        }

        try (Session session = orm.openSession()) {
            assertEquals("B-2", session.selectRow(Employee.class, employee.id).desk.get().code);
            assertNull(session.selectRow(Desk.class, oldDesk.id));
        }
    }

    @Test
    void deleteUserKeepsProfileWithoutRemoveCascade() throws SQLException {
        User user = new User();
        user.name = "Temp";
        Profile profile = new Profile();
        profile.bio = "Keep";
        user.profile = LazyRef.to(profile);
        try (Session session = orm.openSession()) {
            session.insertRow(user);
        }

        try (Session session = orm.openSession()) {
            session.deleteRow(user);
            assertNull(session.selectRow(User.class, user.id));
            assertNotNull(session.selectRow(Profile.class, profile.id));
        }
    }

    @Test
    void deleteUserWithRemoveCascadeDeletesProfile() throws SQLException {
        RemoveUser user = new RemoveUser();
        user.name = "Cascade";
        RemoveProfile profile = new RemoveProfile();
        profile.bio = "Gone";
        user.profile = LazyRef.to(profile);
        try (Session session = orm.openSession()) {
            session.insertRow(user);
        }

        try (Session session = orm.openSession()) {
            session.deleteRow(user);
            assertNull(session.selectRow(RemoveUser.class, user.id));
            assertNull(session.selectRow(RemoveProfile.class, profile.id));
        }
    }

    @Test
    void persistNullableTransientOneToOneWithoutCascadeWritesNullFk() throws SQLException {
        PlainUser user = new PlainUser();
        user.id = UUID.fromString("77777777-7777-4777-8777-777777777777");
        user.name = "No cascade";
        Profile profile = new Profile();
        profile.bio = "Missing";
        user.profile = LazyRef.to(profile);

        try (Session session = orm.openSession()) {
            session.insertRow(user);
            PlainUser loaded = session.selectRow(PlainUser.class, user.id);
            assertEquals("No cascade", loaded.name);
            assertNull(loaded.profile.get());
            assertNull(profile.id);
        }
    }

    @Test
    void persistRejectsRequiredTransientOneToOneWithoutCascade() throws SQLException {
        RequiredUser user = new RequiredUser();
        user.id = UUID.fromString("77777777-7777-4777-8777-777777777778");
        user.name = "Required profile";
        Profile profile = new Profile();
        profile.bio = "Missing";
        user.profile = LazyRef.to(profile);

        try (Session session = orm.openSession()) {
            var error = assertThrows(
                    io.github.connellite.microorm.exception.MicroOrmException.class,
                    () -> session.insertRow(user));
            assertTrue(error.getMessage().contains("transient instance must be saved"));
            assertNull(session.selectRow(RequiredUser.class, user.id));
        }
    }

    @Test
    void persistNullableAssignedIdOneToOneWithoutCascadeWritesNullFkWhenTargetRowIsMissing() throws SQLException {
        PlainUser user = new PlainUser();
        user.id = UUID.fromString("77777777-7777-4777-8777-777777777779");
        user.name = "Assigned missing";
        Profile profile = new Profile();
        profile.id = UUID.fromString("77777777-7777-4777-8777-777777777780");
        profile.bio = "Missing";
        user.profile = LazyRef.to(profile);

        try (Session session = orm.openSession()) {
            session.insertRow(user);
            PlainUser loaded = session.selectRow(PlainUser.class, user.id);
            assertEquals("Assigned missing", loaded.name);
            assertNull(loaded.profile.get());
            assertNull(session.selectRow(Profile.class, profile.id));
        }
    }

    @Test
    void persistRejectsRequiredAssignedIdOneToOneWithoutCascadeWhenTargetRowIsMissing() throws SQLException {
        RequiredUser user = new RequiredUser();
        user.id = UUID.fromString("77777777-7777-4777-8777-777777777781");
        user.name = "Required assigned";
        Profile profile = new Profile();
        profile.id = UUID.fromString("77777777-7777-4777-8777-777777777782");
        profile.bio = "Missing";
        user.profile = LazyRef.to(profile);

        try (Session session = orm.openSession()) {
            var error = assertThrows(
                    io.github.connellite.microorm.exception.MicroOrmException.class,
                    () -> session.insertRow(user));
            assertTrue(error.getMessage().contains("transient instance must be saved"));
            assertNull(session.selectRow(RequiredUser.class, user.id));
        }
    }

    @Test
    void joinQueryFindsUserByProfileBio() throws SQLException {
        User user = new User();
        user.name = "Joiner";
        Profile profile = new Profile();
        profile.bio = "Queryable";
        user.profile = LazyRef.to(profile);
        try (Session session = orm.openSession()) {
            session.insertRow(user);
        }

        try (Session session = orm.openSession()) {
            List<User> found = session.selectRows(EntitySelect.of(User.class)
                    .join("profile")
                    .where(EntitySelect.field("profile.bio").eq("Queryable")));
            assertEquals(1, found.size());
            assertEquals(user.id, found.get(0).id);
        }
    }
}
