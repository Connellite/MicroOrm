package io.github.connellite.microorm.type;

import io.github.connellite.microorm.mapping.EntityField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleJdbcValueMapperTest {

    private final OracleJdbcValueMapper mapper = new OracleJdbcValueMapper();

    @Test
    void writesBooleanAsNumber() throws NoSuchFieldException {
        EntityField field = field("enabled");
        assertEquals(1, mapper.toJdbcValue(field, true));
        assertEquals(0, mapper.toJdbcValue(field, false));
    }

    @Test
    void readsNumberAsBoolean() throws NoSuchFieldException {
        EntityField field = field("enabled");
        assertTrue((Boolean) mapper.fromJdbcValue(field, 1));
        assertFalse((Boolean) mapper.fromJdbcValue(field, 0));
    }

    private static EntityField field(String name) throws NoSuchFieldException {
        Field javaField = Holder.class.getDeclaredField(name);
        return new EntityField(javaField, name, false, false, false);
    }

    static class Holder {
        private boolean enabled;
    }
}
