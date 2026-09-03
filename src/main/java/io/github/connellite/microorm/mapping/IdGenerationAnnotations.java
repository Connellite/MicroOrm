package io.github.connellite.microorm.mapping;

import io.github.connellite.microorm.annotation.GeneratedValue;
import io.github.connellite.microorm.annotation.GenerationType;
import io.github.connellite.microorm.annotation.GenericGenerator;
import io.github.connellite.microorm.annotation.SequenceGenerator;
import io.github.connellite.microorm.annotation.UuidGenerator;
import io.github.connellite.microorm.exception.MicroOrmException;
import io.github.connellite.microorm.generation.IdGeneration;

import java.lang.reflect.Field;
import java.util.Objects;

/** Reads MicroOrm-owned id generation annotations. */
final class IdGenerationAnnotations {

    private static final String NATIVE = "native";

    private IdGenerationAnnotations() {
    }

    static IdGeneration resolve(Class<?> entityClass, Field field) {
        GeneratedValue generatedValue = field.getAnnotation(GeneratedValue.class);
        UuidGenerator uuidGenerator = field.getAnnotation(UuidGenerator.class);
        if (uuidGenerator != null) {
            if (generatedValue != null) {
                throw new MicroOrmException("@UuidGenerator cannot be combined with @GeneratedValue on " + entityClass.getName() + "." + field.getName());
            }
            return IdGeneration.uuid(uuidGenerator.version().number());
        }
        if (generatedValue == null) {
            return IdGeneration.none();
        }

        String generatorName = generatedValue.generator().trim();
        if (!generatorName.isEmpty()) {
            GenericGenerator genericGenerator = findGenericGenerator(entityClass, field, generatorName);
            SequenceGenerator sequenceGenerator = findSequenceGenerator(entityClass, field, generatorName);
            if (genericGenerator != null && sequenceGenerator != null) {
                throw new MicroOrmException("Generator name '" + generatorName + "' is declared more than once on "
                        + entityClass.getName() + "." + field.getName());
            }
            if (genericGenerator != null) {
                return resolveGeneric(entityClass, field, generatedValue, genericGenerator);
            }
            if (sequenceGenerator != null) {
                if (generatedValue.strategy() != GenerationType.SEQUENCE) {
                    throw new MicroOrmException("@SequenceGenerator requires GenerationType.SEQUENCE on " + entityClass.getName() + "." + field.getName());
                }
                return resolveSequence(generatorName, sequenceGenerator);
            }
            throw new MicroOrmException("@GeneratedValue references unknown generator '" + generatorName + "' on " + entityClass.getName() + "." + field.getName());
        }

        if (generatedValue.strategy() == GenerationType.SEQUENCE) {
            SequenceGenerator sequenceGenerator = findUnnamedSequenceGenerator(entityClass, field);
            return sequenceGenerator == null
                    ? IdGeneration.sequence("", "", IdGeneration.DEFAULT_ALLOCATION_SIZE, IdGeneration.DEFAULT_INITIAL_VALUE)
                    : resolveSequence("", sequenceGenerator);
        }
        return IdGeneration.identity("");
    }

    private static IdGeneration resolveGeneric(
            Class<?> entityClass,
            Field field,
            GeneratedValue generatedValue,
            GenericGenerator generator) {
        if (!NATIVE.equalsIgnoreCase(generator.strategy().trim())) {
            throw new MicroOrmException("Unsupported @GenericGenerator strategy '" + generator.strategy() + "' on " + entityClass.getName() + "." + field.getName());
        }
        if (generatedValue.strategy() == GenerationType.SEQUENCE) {
            throw new MicroOrmException("@GenericGenerator(strategy = \"native\") cannot be used with GenerationType.SEQUENCE on " + entityClass.getName() + "." + field.getName());
        }
        return IdGeneration.identity(generator.name().trim());
    }

    private static IdGeneration resolveSequence(String generatorName, SequenceGenerator generator) {
        return IdGeneration.sequence(
                generatorName,
                generator.sequenceName().trim(),
                generator.allocationSize(),
                generator.initialValue());
    }

    private static GenericGenerator findGenericGenerator(Class<?> entityClass, Field field, String name) {
        GenericGenerator fieldGenerator = field.getAnnotation(GenericGenerator.class);
        GenericGenerator typeGenerator = entityClass.getAnnotation(GenericGenerator.class);
        return named(name, fieldGenerator) ? fieldGenerator : (named(name, typeGenerator) ? typeGenerator : null);
    }

    private static SequenceGenerator findSequenceGenerator(Class<?> entityClass, Field field, String name) {
        SequenceGenerator fieldGenerator = field.getAnnotation(SequenceGenerator.class);
        SequenceGenerator typeGenerator = entityClass.getAnnotation(SequenceGenerator.class);
        return named(name, fieldGenerator) ? fieldGenerator : (named(name, typeGenerator) ? typeGenerator : null);
    }

    private static SequenceGenerator findUnnamedSequenceGenerator(Class<?> entityClass, Field field) {
        SequenceGenerator fieldGenerator = field.getAnnotation(SequenceGenerator.class);
        if (fieldGenerator != null && fieldGenerator.name().isBlank()) {
            return fieldGenerator;
        }
        SequenceGenerator typeGenerator = entityClass.getAnnotation(SequenceGenerator.class);
        return typeGenerator != null && typeGenerator.name().isBlank() ? typeGenerator : null;
    }

    private static boolean named(String name, GenericGenerator generator) {
        return generator != null && Objects.equals(name, generator.name().trim());
    }

    private static boolean named(String name, SequenceGenerator generator) {
        return generator != null && Objects.equals(name, generator.name().trim());
    }
}
