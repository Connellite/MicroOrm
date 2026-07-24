package io.github.connellite.microorm.mapping;

/** Immutable metadata for a table or column check constraint. */
public record TableCheck(String name, String constraints) {
    public TableCheck {
        name = name == null ? "" : name;
        if (constraints == null || constraints.isBlank()) {
            throw new IllegalArgumentException("check constraint cannot be blank");
        }
    }
}
