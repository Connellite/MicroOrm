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
        assertEquals("URL", LambdaFieldResolver.fieldName((EntityQuery.Getter<AccessorNames, String>) AccessorNames::getURL));
        assertEquals("nMetaType", LambdaFieldResolver.fieldName(
                (EntityQuery.Getter<AccessorNames, String>) AccessorNames::getnMetaType));
        assertEquals("NMetaType", LambdaFieldResolver.fieldName(
                (EntityQuery.Getter<AccessorNames, String>) AccessorNames::getNMetaType));
        assertEquals("enabled", LambdaFieldResolver.fieldName(
                (EntityQuery.Getter<AccessorNames, Boolean>) AccessorNames::isEnabled));
    }

    @Test
    void rejectsPlainLambdaExpressions() {
        assertThrows(IllegalArgumentException.class,
                () -> LambdaFieldResolver.fieldName(
                        (EntityQuery.Getter<AccessorNames, String>) value -> value.getURL()));
    }
}
