/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
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
 */
public final class TypeSchemaExtractor {

    private TypeSchemaExtractor() {
    }

    public static Map<String, Object> extract(Type type) {
        return extract(type, new LinkedHashSet<>());
    }

    private static Map<String, Object> extract(Type type, Set<Type> visited) {
        if (type == null) {
            return Map.of();
        }
        if (visited.contains(type)) {
            return Map.of("type", "object");
        }

        if (type instanceof Class<?> clazz) {
            if (clazz == String.class || clazz == Character.class || clazz == char.class || clazz == UUID.class) {
                return new LinkedHashMap<>(Map.of("type", "string"));
            }
            if (clazz == Integer.class || clazz == int.class
                    || clazz == Long.class || clazz == long.class
                    || clazz == Short.class || clazz == short.class
                    || clazz == Byte.class || clazz == byte.class
                    || clazz == BigInteger.class) {
                return new LinkedHashMap<>(Map.of("type", "integer"));
            }
            if (clazz == Float.class || clazz == float.class
                    || clazz == Double.class || clazz == double.class
                    || clazz == BigDecimal.class) {
                return new LinkedHashMap<>(Map.of("type", "number"));
            }
            if (clazz == Boolean.class || clazz == boolean.class) {
                return new LinkedHashMap<>(Map.of("type", "boolean"));
            }
            if (clazz == LocalDateTime.class) {
                return new LinkedHashMap<>(Map.of("type", "string", "format", "date-time"));
            }
            if (clazz == LocalDate.class) {
                return new LinkedHashMap<>(Map.of("type", "string", "format", "date"));
            }
            if (clazz == LocalTime.class) {
                return new LinkedHashMap<>(Map.of("type", "string", "format", "time"));
            }
            if (clazz == byte[].class) {
                return new LinkedHashMap<>(Map.of("type", "string", "format", "binary"));
            }
            if (clazz.isEnum()) {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "string");
                List<String> values = new ArrayList<>();
                for (Object constant : clazz.getEnumConstants()) {
                    values.add(String.valueOf(constant));
                }
                schema.put("enum", values);
                return schema;
            }
            if (clazz.isArray()) {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "array");
                schema.put("items", extract(clazz.getComponentType(), visited));
                return schema;
            }
            if (List.class.isAssignableFrom(clazz) || Set.class.isAssignableFrom(clazz)) {
                return new LinkedHashMap<>(Map.of("type", "array"));
            }
            if (Map.class.isAssignableFrom(clazz)) {
                return new LinkedHashMap<>(Map.of("type", "object"));
            }
            if (Object.class == clazz) {
                return new LinkedHashMap<>(Map.of("type", "object"));
            }

            visited.add(type);
            try {
                return extractPojoSchema(clazz, visited);
            } finally {
                visited.remove(type);
            }
        }

        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType == Optional.class) {
                Map<String, Object> schema = extract(parameterizedType.getActualTypeArguments()[0], visited);
                schema.put("nullable", true);
                return schema;
            }
            if (rawType == List.class || rawType == Set.class) {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "array");
                schema.put("items", extract(parameterizedType.getActualTypeArguments()[0], visited));
                return schema;
            }
            if (rawType == Map.class) {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                if (parameterizedType.getActualTypeArguments().length > 1) {
                    schema.put("additionalProperties", extract(parameterizedType.getActualTypeArguments()[1], visited));
                }
                return schema;
            }
            return extract(rawType, visited);
        }

        return new LinkedHashMap<>(Map.of("type", "object"));
    }

    private static Map<String, Object> extractPojoSchema(Class<?> clazz, Set<Type> visited) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
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
}
