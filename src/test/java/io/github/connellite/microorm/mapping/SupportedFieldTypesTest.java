package io.github.connellite.microorm.mapping;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedFieldTypesTest {

    enum Status {
        OPEN, CLOSED
    }

    @Test
    void acceptsPrimitiveAndWrapperScalars() {
        assertTrue(SupportedFieldTypes.isSupported(long.class));
        assertTrue(SupportedFieldTypes.isSupported(Long.class));
        assertTrue(SupportedFieldTypes.isSupported(int.class));
        assertTrue(SupportedFieldTypes.isSupported(boolean.class));
        assertTrue(SupportedFieldTypes.isSupported(char.class));
        assertTrue(SupportedFieldTypes.isSupported(String.class));
    }

    @Test
    void acceptsJdbcAndTemporalTypes() {
        assertTrue(SupportedFieldTypes.isSupported(BigDecimal.class));
        assertTrue(SupportedFieldTypes.isSupported(java.sql.Date.class));
        assertTrue(SupportedFieldTypes.isSupported(Timestamp.class));
        assertTrue(SupportedFieldTypes.isSupported(Date.class));
        assertTrue(SupportedFieldTypes.isSupported(LocalDateTime.class));
        assertTrue(SupportedFieldTypes.isSupported(UUID.class));
    }

    @Test
    void acceptsEnums() {
        assertTrue(SupportedFieldTypes.isSupported(Status.class));
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertFalse(SupportedFieldTypes.isSupported(List.class));
        assertFalse(SupportedFieldTypes.isSupported(Object.class));
    }
}
