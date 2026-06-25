/**
 * Lightweight annotation-driven JDBC ORM.
 */
module io.github.connellite.microorm {
    requires io.github.connellite.ExtraLib;
    requires java.sql;

    exports io.github.connellite.microorm;
    exports io.github.connellite.microorm.annotation;
    exports io.github.connellite.microorm.connection;
    exports io.github.connellite.microorm.dialect;
    exports io.github.connellite.microorm.mapping;
    exports io.github.connellite.microorm.session;
    exports io.github.connellite.microorm.sql;
    exports io.github.connellite.microorm.type;
    exports io.github.connellite.microorm.relation;

    opens io.github.connellite.microorm to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.annotation to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.connection to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.dialect to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.jdbc to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.mapping to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.schema to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.session to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.sql to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.relation to io.github.connellite.ExtraLib;
    opens io.github.connellite.microorm.type to io.github.connellite.ExtraLib;
}
