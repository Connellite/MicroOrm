package io.github.connellite.microorm.schema;

import io.github.connellite.microorm.annotation.Id;

public class PackageAnnotatedEntity {

    @Id
    private long id;

    private String name;
}
