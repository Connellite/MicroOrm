package io.github.connellite.microorm.type;

import io.github.connellite.microorm.mapping.EntityField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultJdbcValueMapperTest {

    @Test
    void writesAndReadsMicrosoftGuidBinaryLayout() throws NoSuchFieldException {
        DefaultJdbcValueMapper mapper = new DefaultJdbcValueMapper(UuidStorage.MICROSOFT_GUID);
        EntityField field = field("id");
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        byte[] bytes = (byte[]) mapper.toJdbcValue(field, uuid);

        assertArrayEquals(new byte[]{
                0x00, (byte) 0x84, 0x0e, 0x55,
                (byte) 0x9b, (byte) 0xe2,
                (byte) 0xd4, 0x41,
                (byte) 0xa7, 0x16, 0x44, 0x66, 0x55, 0x44, 0x00, 0x00
        }, bytes);
        assertEquals(uuid, mapper.fromJdbcValue(field, bytes));
    }

    private static EntityField field(String name) throws NoSuchFieldException {
        Field javaField = Holder.class.getDeclaredField(name);
        return new EntityField(javaField, name, false, false, false);
    }

    static class Holder {
        private UUID id;
    }
}
