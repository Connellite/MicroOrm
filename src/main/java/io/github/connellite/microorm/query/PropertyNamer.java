package io.github.connellite.microorm.query;

import java.util.Locale;

/**
 * Converts JavaBean accessor names to property names.
 */
final class PropertyNamer {

    private PropertyNamer() {
    }

    // Mirrors MyBatis PropertyNamer.methodToProperty:
    // https://mybatis.org/mybatis-3/xref/org/apache/ibatis/reflection/property/PropertyNamer.html
    static String methodToProperty(String name) {
        if (name.startsWith("is")) {
            name = name.substring(2);
        } else if (name.startsWith("get") || name.startsWith("set")) {
            name = name.substring(3);
        } else {
            throw new IllegalArgumentException(
                    "Error parsing property name '" + name + "'. Didn't start with 'is', 'get' or 'set'.");
        }

        if (name.length() == 1 || name.length() > 1 && !Character.isUpperCase(name.charAt(1))) {
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }
        return name;
    }
}
