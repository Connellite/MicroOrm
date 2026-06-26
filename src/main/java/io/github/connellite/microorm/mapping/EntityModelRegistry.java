package io.github.connellite.microorm.mapping;

import io.github.connellite.collections.ConcurrentReferenceHashMap;
import io.github.connellite.reflection.ReflectionUtil;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.annotation.Column;
import io.github.connellite.microorm.annotation.Entity;
import io.github.connellite.microorm.annotation.Id;
import io.github.connellite.microorm.annotation.JoinColumn;
import io.github.connellite.microorm.annotation.ManyToOne;
import io.github.connellite.microorm.annotation.OneToMany;
import io.github.connellite.microorm.annotation.Transient;
import io.github.connellite.microorm.relation.LazyCollection;
import io.github.connellite.microorm.relation.LazyRef;
import io.github.connellite.microorm.sql.SqlGenerator;
import io.github.connellite.microorm.sql.SqlIdentifier;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Thread-safe registry of entity metadata built from annotations. */
public final class EntityModelRegistry {

    private static final int CACHE_INITIAL_CAPACITY = 64;

    private final PhysicalNamingStrategy physicalNamingStrategy;
    private final Set<Class<?>> registered = ConcurrentHashMap.newKeySet();
    private final ConcurrentReferenceHashMap<Class<?>, EntityModel> cache =
            new ConcurrentReferenceHashMap<>(CACHE_INITIAL_CAPACITY, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    public EntityModelRegistry() {
        this(IdentityPhysicalNamingStrategy.INSTANCE);
    }

    public EntityModelRegistry(PhysicalNamingStrategy physicalNamingStrategy) {
        this.physicalNamingStrategy = Objects.requireNonNull(physicalNamingStrategy, "physicalNamingStrategy");
    }

    /** Returns metadata for a previously {@link #register(Class) registered} entity. */
    public EntityModel get(Class<?> entityClass) {
        if (!registered.contains(entityClass)) {
            throw new MicroOrmException("Entity not registered: " + entityClass.getName());
        }
        return cache.computeIfAbsent(entityClass, this::build);
    }

    /** Registers an entity class and builds or returns cached metadata. */
    public EntityModel register(Class<?> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass");
        registered.add(entityClass);
        return cache.computeIfAbsent(entityClass, this::build);
    }

    private EntityModel build(Class<?> entityClass) {
        Entity entityAnn = entityClass.getAnnotation(Entity.class);
        if (entityAnn == null) {
            throw new MicroOrmException("Missing @Entity on " + entityClass.getName());
        }
        SqlIdentifier table = entityAnn.name().isBlank()
                ? toPhysicalTable(SqlIdentifier.unquoted(entityClass.getSimpleName()))
                : toPhysicalTable(SqlIdentifier.parse(entityAnn.name()));
        SqlGenerator.validateIdentifier(table.text(), "table");
        try {
            ReflectionUtil.getConstructor(entityClass);
        } catch (NoSuchMethodException e) {
            throw new MicroOrmException("Entity requires a no-arg constructor: " + entityClass.getName(), e);
        }

        rejectInheritedMappedFields(entityClass);

        List<EntityField> fields = new ArrayList<>();
        List<ManyToOneField> manyToOneRelations = new ArrayList<>();
        List<OneToManyField> oneToManyRelations = new ArrayList<>();
        EntityField pk = null;
        for (Field f : entityClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.getAnnotation(Transient.class) != null) {
                continue;
            }
            f.trySetAccessible();
            if (LazyRef.class.isAssignableFrom(f.getType())) {
                manyToOneRelations.add(buildManyToOne(entityClass, f));
                continue;
            }
            if (LazyCollection.class.isAssignableFrom(f.getType())) {
                oneToManyRelations.add(buildOneToMany(entityClass, f));
                continue;
            }
            Id idAnn = f.getAnnotation(Id.class);
            Column colAnn = f.getAnnotation(Column.class);
            if (idAnn != null) {
                if (pk != null) {
                    throw new MicroOrmException("Multiple @Id fields on " + entityClass.getName());
                }
                validateIdField(entityClass, f, idAnn);
                SqlIdentifier col = toPhysicalColumn(columnIdentifier(f, colAnn));
                boolean nullable = colAnn != null && colAnn.nullable();
                pk = new EntityField(
                        f,
                        col,
                        true,
                        idAnn.autoIncrement(),
                        nullable,
                        colAnn != null && colAnn.unique(),
                        colAnn != null && colAnn.indexed(),
                        colAnn == null ? "" : colAnn.sqlType(),
                        colAnn == null ? 0 : colAnn.length());
                fields.add(pk);
            } else {
                validateFieldType(entityClass, f);
                SqlIdentifier col = toPhysicalColumn(columnIdentifier(f, colAnn));
                boolean nullable = colAnn == null || colAnn.nullable();
                fields.add(new EntityField(
                        f,
                        col,
                        false,
                        false,
                        nullable,
                        colAnn != null && colAnn.unique(),
                        colAnn != null && colAnn.indexed(),
                        colAnn == null ? "" : colAnn.sqlType(),
                        colAnn == null ? 0 : colAnn.length()));
            }
        }
        if (pk == null) {
            throw new MicroOrmException("Missing @Id on " + entityClass.getName());
        }
        EntityModel model = new EntityModel(entityClass, table, fields, pk, manyToOneRelations, oneToManyRelations);
        SqlGenerator.validateColumnNames(model);
        return model;
    }

    private ManyToOneField buildManyToOne(Class<?> entityClass, Field field) {
        if (field.getAnnotation(ManyToOne.class) == null) {
            throw new MicroOrmException("LazyRef field requires @ManyToOne on "
                    + entityClass.getName() + "." + field.getName());
        }
        Class<?> targetType = resolveLazyRefTarget(entityClass, field);
        requireEntity(targetType);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        SqlIdentifier column = joinColumn != null && !joinColumn.name().isBlank()
                ? toPhysicalColumn(SqlIdentifier.parse(joinColumn.name()))
                : toPhysicalColumn(SqlIdentifier.unquoted(field.getName() + "_id"));
        boolean nullable = joinColumn == null || joinColumn.nullable();
        Class<?> fkType = primaryKeyJavaType(targetType);
        return new ManyToOneField(field, targetType, column, nullable, fkType);
    }

    private static Class<?> primaryKeyJavaType(Class<?> entityClass) {
        for (Field f : entityClass.getDeclaredFields()) {
            if (f.getAnnotation(Id.class) != null) {
                return f.getType();
            }
        }
        throw new MicroOrmException("Missing @Id on " + entityClass.getName());
    }

    private static OneToManyField buildOneToMany(Class<?> entityClass, Field field) {
        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        if (oneToMany == null) {
            throw new MicroOrmException("LazyCollection field requires @OneToMany on "
                    + entityClass.getName() + "." + field.getName());
        }
        String mappedBy = oneToMany.mappedBy();
        if (mappedBy.isBlank()) {
            throw new MicroOrmException("@OneToMany.mappedBy is required on "
                    + entityClass.getName() + "." + field.getName());
        }
        Class<?> childType = resolveLazyCollectionTarget(entityClass, field);
        requireEntity(childType);
        validateInverseManyToOne(childType, mappedBy, entityClass);
        return new OneToManyField(field, childType, mappedBy);
    }

    private static void validateInverseManyToOne(Class<?> childClass, String mappedByField, Class<?> ownerClass) {
        Field inverse;
        try {
            inverse = childClass.getDeclaredField(mappedByField);
        } catch (NoSuchFieldException e) {
            throw new MicroOrmException("mappedBy field '" + mappedByField + "' not found on " + childClass.getName(), e);
        }
        if (inverse.getAnnotation(ManyToOne.class) == null) {
            throw new MicroOrmException("mappedBy field must have @ManyToOne on "
                    + childClass.getName() + "." + mappedByField);
        }
        if (!LazyRef.class.isAssignableFrom(inverse.getType())) {
            throw new MicroOrmException("mappedBy field must be LazyRef on "
                    + childClass.getName() + "." + mappedByField);
        }
        Class<?> inverseTarget = resolveLazyRefTarget(childClass, inverse);
        if (inverseTarget != ownerClass) {
            throw new MicroOrmException("mappedBy @ManyToOne must reference " + ownerClass.getName()
                    + " on " + childClass.getName() + "." + mappedByField);
        }
    }

    private static Class<?> resolveLazyRefTarget(Class<?> entityClass, Field field) {
        List<Class<?>> typeArgs = ReflectionUtil.getAllGenericParameterClasses(field);
        if (typeArgs.isEmpty()) {
            throw new MicroOrmException("LazyRef field requires a type argument on "
                    + entityClass.getName() + "." + field.getName());
        }
        return typeArgs.get(0);
    }

    private static Class<?> resolveLazyCollectionTarget(Class<?> entityClass, Field field) {
        List<Class<?>> typeArgs = ReflectionUtil.getAllGenericParameterClasses(field);
        if (typeArgs.isEmpty()) {
            throw new MicroOrmException("LazyCollection field requires a type argument on "
                    + entityClass.getName() + "." + field.getName());
        }
        return typeArgs.get(0);
    }

    private static void requireEntity(Class<?> type) {
        if (type.getAnnotation(Entity.class) == null) {
            throw new MicroOrmException("Association target must be @Entity: " + type.getName());
        }
    }

    private static void validateIdField(Class<?> entityClass, Field field, Id idAnn) {
        validateFieldType(entityClass, field);
        Class<?> type = ReflectionUtil.primitiveToWrapper(field.getType());
        boolean numeric = Number.class.isAssignableFrom(type);
        boolean uuid = type == UUID.class;
        if (!numeric && !uuid) {
            throw new MicroOrmException("@Id field must be numeric or UUID on "
                    + entityClass.getName() + "." + field.getName());
        }
        if (idAnn.autoIncrement() && !numeric) {
            throw new MicroOrmException("@Id(autoIncrement = true) requires a numeric field on "
                    + entityClass.getName() + "." + field.getName());
        }
    }

    private SqlIdentifier toPhysicalTable(SqlIdentifier logical) {
        return toPhysical(logical, physicalNamingStrategy::toPhysicalTableName);
    }

    private SqlIdentifier toPhysicalColumn(SqlIdentifier logical) {
        return toPhysical(logical, physicalNamingStrategy::toPhysicalColumnName);
    }

    private static SqlIdentifier toPhysical(SqlIdentifier logical, Function<String, String> transform) {
        if (logical.quoted()) {
            return logical;
        }
        return SqlIdentifier.unquoted(transform.apply(logical.text()));
    }

    private static SqlIdentifier columnIdentifier(Field f, Column colAnn) {
        if (colAnn != null && !colAnn.name().isBlank()) {
            return SqlIdentifier.parse(colAnn.name());
        }
        return SqlIdentifier.unquoted(f.getName());
    }

    private static void validateFieldType(Class<?> entityClass, Field field) {
        if (!SupportedFieldTypes.isSupported(field.getType())) {
            throw new MicroOrmException("Unsupported field type " + field.getType().getName()
                    + " on " + entityClass.getName() + "." + field.getName());
        }
        Column colAnn = field.getAnnotation(Column.class);
        if (colAnn != null && !colAnn.sqlType().isBlank()) {
            SqlGenerator.validateSqlType(colAnn.sqlType(), entityClass.getName() + "." + field.getName());
        }
    }

    private static void rejectInheritedMappedFields(Class<?> entityClass) {
        Class<?> superClass = entityClass.getSuperclass();
        if (superClass == null || superClass == Object.class) {
            return;
        }
        for (Field f : superClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.getAnnotation(Transient.class) != null) {
                continue;
            }
            if (f.getAnnotation(Id.class) != null
                    || f.getAnnotation(Column.class) != null
                    || f.getAnnotation(ManyToOne.class) != null
                    || f.getAnnotation(OneToMany.class) != null) {
                throw new MicroOrmException("Entity inheritance is not supported; move mapped fields to "
                        + entityClass.getName() + " (found " + superClass.getName() + "." + f.getName() + ")");
            }
        }
    }
}
