package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.annotation.CascadeType;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/** Shared metadata for {@link OneToManyField} and {@link ManyToManyField} collection wrappers. */
public interface CollectionRelation {

    Field javaField();

    VarHandle varHandle();

    Class<?> targetEntityClass();

    boolean cascades(CascadeType type);
}
