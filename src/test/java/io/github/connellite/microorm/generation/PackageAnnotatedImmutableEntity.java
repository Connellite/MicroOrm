package io.github.connellite.microorm.generation;

import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.Table;

@Entity
@Table(name = "package_immutable_items")
public class PackageAnnotatedImmutableEntity {

    @Id
    private long id;

    private String name;
}
