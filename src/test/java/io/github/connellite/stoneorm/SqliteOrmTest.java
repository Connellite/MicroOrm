package io.github.connellite.stoneorm;

import io.github.connellite.stoneorm.annotation.Column;
import io.github.connellite.stoneorm.annotation.Entity;
import io.github.connellite.stoneorm.annotation.Id;
import io.github.connellite.stoneorm.session.Session;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SqliteOrmTest {

    @Entity(name = "widgets")
    public static class Widget {
        @Id(autoIncrement = true)
        private long id;

        @Column(nullable = false)
        private String name;

        public Widget() {
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    void crudLifecycle() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);

                Widget w = new Widget();
                w.setName("hello");
                s.insertRow(w);
                assertEquals(1L, w.getId());

                Widget loaded = s.selectRow(Widget.class, 1L);
                assertNotNull(loaded);
                assertEquals("hello", loaded.getName());

                loaded.setName("world");
                assertEquals(1, s.updateRow(loaded));

                List<Widget> all = s.selectRows(Widget.class);
                assertEquals(1, all.size());
                assertEquals("world", all.get(0).getName());

                assertEquals(1, s.deleteRow(loaded));
                assertNull(s.selectRow(Widget.class, 1L));

                s.insertRow(newWidget("a"));
                s.insertRow(newWidget("b"));
                assertEquals(2, s.deleteAllRows(Widget.class));
                assertEquals(0, s.selectRows(Widget.class).size());
            }
        }
    }

    private static Widget newWidget(String name) {
        Widget w = new Widget();
        w.setName(name);
        return w;
    }

    @Test
    void transactionCommit() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Orm orm = Orm.sqlite(c).register(Widget.class);
            try (Session s = orm.openSession()) {
                s.dropEntity(Widget.class);
                s.createEntity(Widget.class);
                s.beginTransaction();
                Widget w = new Widget();
                w.setName("tx");
                s.insertRow(w);
                s.commitTransaction();
            }
            try (Session s2 = orm.openSession()) {
                Widget w = s2.selectRow(Widget.class, 1L);
                assertNotNull(w);
                assertEquals("tx", w.getName());
            }
        }
    }
}
