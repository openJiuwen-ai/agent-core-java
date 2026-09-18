/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reflection-based schema extraction for Java types.
 *
 * <p>Mirrors Python's type schema extractor helpers in
 * {@code openjiuwen/core/foundation/tool/utils/type_schema_extractor.py}.</p>
 */
public final class TypeSchemaExtractor {

    private TypeSchemaExtractor() {
    }

    public static Map<String, Object> extract(Type type) {
        return extract(type, new LinkedHashSet<>());
    }

    private static Map<String, Object> extract(Type type, Set<Type> visited) {
        if (type == null) {
            return new LinkedHashMap<>();
        }
        if (visited.contains(type)) {
            return linkedMap("type", "object");
        }

        if (type instanceof Class<?> clazz) {
            return extractClass(clazz, type, visited);
        }

        if (type instanceof ParameterizedType parameterizedType) {
            return extractParameterized(parameterizedType, visited);
        }

        return linkedMap("type", "object");
    }

    private static Map<String, Object> extractClass(Class<?> clazz, Type type, Set<Type> visited) {
        if (clazz == String.class || clazz == Character.class || clazz == char.class || clazz == UUID.class) {
            return linkedMap("type", "string");
        }
        if (clazz == Path.class) {
            return linkedMap("type", "string", "format", "path");
        }
        if (clazz == byte[].class || clazz == Byte[].class) {
            return linkedMap("type", "string", "format", "binary");
        }
        if (clazz == Integer.class || clazz == int.class
                || clazz == Long.class || clazz == long.class
                || clazz == Short.class || clazz == short.class
                || clazz == Byte.class || clazz == byte.class
                || clazz == BigInteger.class) {
            return linkedMap("type", "integer");
        }
        if (clazz == Float.class || clazz == float.class
                || clazz == Double.class || clazz == double.class
                || clazz == BigDecimal.class) {
            return linkedMap("type", "number");
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return linkedMap("type", "boolean");
        }
        if (clazz == LocalDateTime.class) {
            return linkedMap("type", "string", "format", "date-time");
        }
        if (clazz == LocalDate.class) {
            return linkedMap("type", "string", "format", "date");
        }
        if (clazz == LocalTime.class) {
            return linkedMap("type", "string", "format", "time");
        }
        if (clazz.isEnum()) {
            Map<String, Object> schema = linkedMap("type", "string");
            List<String> values = new ArrayList<>();
            for (Object constant : clazz.getEnumConstants()) {
                values.add(String.valueOf(constant));
            }
            schema.put("enum", values);
            return schema;
        }
        if (clazz.isArray()) {
            Map<String, Object> schema = linkedMap("type", "array");
            schema.put("items", extract(clazz.getComponentType(), visited));
            return schema;
        }
        if (List.class.isAssignableFrom(clazz)) {
            return linkedMap("type", "array");
        }
        if (Set.class.isAssignableFrom(clazz)) {
            return linkedMap("type", "array", "uniqueItems", true);
        }
        if (Map.class.isAssignableFrom(clazz)) {
            return linkedMap("type", "object");
        }
        if (Object.class == clazz) {
            return linkedMap("type", "object");
        }

        visited.add(type);
        try {
            return extractPojoSchema(clazz, visited);
        } finally {
            visited.remove(type);
        }
    }

    private static Map<String, Object> extractParameterized(ParameterizedType parameterizedType, Set<Type> visited) {
        Type rawType = parameterizedType.getRawType();
        Type[] arguments = parameterizedType.getActualTypeArguments();
        if (rawType == Optional.class) {
            Map<String, Object> schema = extract(arguments[0], visited);
            schema.put("nullable", true);
            return schema;
        }
        if (rawType == List.class) {
            Map<String, Object> schema = linkedMap("type", "array");
            schema.put("items", extract(arguments[0], visited));
            return schema;
        }
        if (rawType == Set.class) {
            Map<String, Object> schema = linkedMap("type", "array", "uniqueItems", true);
            schema.put("items", extract(arguments[0], visited));
            return schema;
        }
        if (rawType == Map.class) {
            Map<String, Object> schema = linkedMap("type", "object");
            if (arguments.length > 1) {
                schema.put("additionalProperties", extract(arguments[1], visited));
            }
            return schema;
        }
        return extract(rawType, visited);
    }

    private static Map<String, Object> extractPojoSchema(Class<?> clazz, Set<Type> visited) {
        Map<String, Object> schema = linkedMap("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            Map<String, Object> fieldSchema = extract(field.getGenericType(), visited);
            fieldSchema.putIfAbsent("description", CallableSchemaExtractor.humanizeName(field.getName()));
            properties.put(field.getName(), fieldSchema);
            if (!Optional.class.isAssignableFrom(field.getType())) {
                required.add(field.getName());
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private static Map<String, Object> linkedMap(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1]);
        }
        return values;
    }
}
