package io.github.connellite.microorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lazy many-to-one association. Field type must be {@link io.github.connellite.microorm.relation.LazyRef}
 * with a type argument pointing at the target {@link Entity}. Pair with {@link JoinColumn} for the FK column.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManyToOne {
}
