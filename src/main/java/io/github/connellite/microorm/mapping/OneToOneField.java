package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.annotation.CascadeType;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.reflection.MethodHandleReflectionUtil;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Set;

/** Metadata for a {@link io.github.connellite.microorm.annotation.OneToOne} reference wrapper field. */
public final class OneToOneField {

    private final Field javaField;
    private final VarHandle varHandle;
    private final Class<?> targetEntityClass;
    private final String mappedBy;
    private final Set<CascadeType> cascade;
    private final boolean orphanRemoval;

    public OneToOneField(
            Field javaField,
            Class<?> targetEntityClass,
            String mappedBy,
            CascadeType[] cascade,
            boolean orphanRemoval) {
        this.javaField = javaField;
        this.targetEntityClass = targetEntityClass;
        this.mappedBy = mappedBy == null ? "" : mappedBy;
        this.cascade = Cascades.copyCascade(cascade);
        this.orphanRemoval = orphanRemoval;
        try {
            this.varHandle = MethodHandleReflectionUtil.varHandle(javaField);
        } catch (IllegalAccessException e) {
            throw new MicroOrmException("Cannot access field " + javaField.getName(), e);
        }
    }

    public Field javaField() {
        return javaField;
    }

    public VarHandle varHandle() {
        return varHandle;
    }

    public Class<?> targetEntityClass() {
        return targetEntityClass;
    }

    public String mappedBy() {
        return mappedBy;
    }

    public boolean owning() {
        return mappedBy.isBlank();
    }

    public boolean orphanRemoval() {
        return orphanRemoval;
    }

    public boolean cascades(CascadeType type) {
        return Cascades.enabled(cascade, type);
    }

    /**
     * Owning-side metadata: this field, or the {@code mappedBy} field on the target entity.
     */
    public OneToOneField owningSide(EntityModelRegistry registry) {
        if (owning()) {
            return this;
        }
        EntityModel targetModel = registry.get(targetEntityClass);
        OneToOneField owning = targetModel.oneToOneByFieldName(mappedBy);
        if (!owning.owning()) {
            throw new MicroOrmException("mappedBy '" + mappedBy + "' on " + javaField.getDeclaringClass().getName()
                    + "." + javaField.getName() + " must refer to the owning @OneToOne");
        }
        return owning;
    }
}
