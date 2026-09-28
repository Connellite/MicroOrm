package io.github.connellite.microorm.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LambdaFieldResolverTest {

    static class AccessorNames {
        public String getURL() {
            return null;
        }

        public String getnMetaType() {
            return null;
        }

        public String getNMetaType() {
            return null;
        }

        public boolean isEnabled() {
            return true;
        }
    }

    @Test
    void resolvesGetterReferencesUsingMyBatisPropertyNamingRules() {
        assertEquals("URL", EntitySelect.field((EntitySelect.Getter<AccessorNames, String>) AccessorNames::getURL).name());
        assertEquals("nMetaType", EntitySelect.field(
                (EntitySelect.Getter<AccessorNames, String>) AccessorNames::getnMetaType).name());
        assertEquals("NMetaType", EntitySelect.field(
                (EntitySelect.Getter<AccessorNames, String>) AccessorNames::getNMetaType).name());
        assertEquals("enabled", EntitySelect.field(
                (EntitySelect.Getter<AccessorNames, Boolean>) AccessorNames::isEnabled).name());
    }

    @Test
    void rejectsPlainLambdaExpressions() {
        assertThrows(IllegalArgumentException.class,
                () -> EntitySelect.field(
                        (EntitySelect.Getter<AccessorNames, String>) value -> value.getURL()));
    }
}
