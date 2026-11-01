package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.annotation.PostLoad;
import io.github.connellite.microorm.annotation.PostPersist;
import io.github.connellite.microorm.annotation.PostRemove;
import io.github.connellite.microorm.annotation.PostUpdate;
import io.github.connellite.microorm.annotation.PrePersist;
import io.github.connellite.microorm.annotation.PreRemove;
import io.github.connellite.microorm.annotation.PreUpdate;

import java.lang.annotation.Annotation;

/** Entity lifecycle events supported by MicroOrm callback annotations. */
public enum LifecycleEvent {
    PRE_PERSIST(PrePersist.class),
    POST_PERSIST(PostPersist.class),
    PRE_UPDATE(PreUpdate.class),
    POST_UPDATE(PostUpdate.class),
    PRE_REMOVE(PreRemove.class),
    POST_REMOVE(PostRemove.class),
    POST_LOAD(PostLoad.class);

    private final Class<? extends Annotation> annotationType;

    LifecycleEvent(Class<? extends Annotation> annotationType) {
        this.annotationType = annotationType;
    }

    Class<? extends Annotation> annotationType() {
        return annotationType;
    }

    /** {@code @PostPersist}, {@code @PostUpdate}, {@code @PostRemove}, and {@code @PostLoad}. */
    boolean post() {
        return switch (this) {
            case POST_PERSIST, POST_UPDATE, POST_REMOVE, POST_LOAD -> true;
            case PRE_PERSIST, PRE_UPDATE, PRE_REMOVE -> false;
        };
    }
}
