/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;

import java.util.*;

/**
 * Schema utility class for handling JSON Schema validation, data formatting,
 * and default value population.
 * <p>
 * Java equivalent of Python's {@code SchemaUtils}. Uses Jackson for JSON
 * processing. Provides:
 * <ul>
 *   <li>{@link #formatWithSchema} — format data according to schema, filling defaults</li>
 *   <li>{@link #validateWithSchema} — validate data against a JSON Schema</li>
 *   <li>{@link #getSchemaDict} — extract schema from a class via Jackson</li>
 * </ul>
 */
public final class SchemaUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SchemaUtils() {
    }

    /**
     * Format data according to the provided JSON Schema, filling in default values
     * for missing properties.
     *
     * @param data   the data to format (may be null)
     * @param schema JSON Schema dictionary
     * @return formatted data with defaults populated
     * @throws ValidationError if data is null or cannot be formatted
     */
    public static Map<String, Object> formatWithSchema(Map<String, Object> data,
                                                       Map<String, Object> schema) {
        return formatWithSchema(data, schema, false, false);
    }

    /**
     * Format data according to the provided JSON Schema, optionally skipping
     * validation while still applying defaults.
     */
    public static Map<String, Object> formatWithSchema(Map<String, Object> data,
                                                       Map<String, Object> schema,
                                                       boolean skipValidate) {
        return formatWithSchema(data, schema, false, skipValidate);
    }

    /**
     * Format data according to the provided JSON Schema, optionally removing null
     * values and/or skipping validation.
     *
     * @param data          the data to format
     * @param schema        JSON Schema dictionary
     * @param skipNoneValue if true, recursively remove null values before formatting
     * @param skipValidate  if true, skip schema validation
     * @return formatted data with defaults populated
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> formatWithSchema(Map<String, Object> data,
                                                       Map<String, Object> schema,
                                                       boolean skipNoneValue,
                                                       boolean skipValidate) {
        if (data == null) {
            throw new ValidationError(StatusCode.SCHEMA_FORMAT_INVALID,
                    null, null, null,
                    Map.of("reason", "data is null", "data", "null"));
        }

        try {
            Map<String, Object> newData = skipNoneValue ? removeNoneValues(data) : data;
            Map<String, Object> dataWithDefaults = applyDefaults(newData, schema);

            if (!skipValidate) {
                validateWithSchema(dataWithDefaults, schema);
            }

            return dataWithDefaults;
        } catch (ValidationError e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationError(StatusCode.SCHEMA_FORMAT_INVALID,
                    null, null, e,
                    Map.of("reason", e.getMessage(), "data", String.valueOf(data)));
        }
    }

    /**
     * Validate data against a JSON Schema dictionary.
     * <p>
     * Performs basic structural validation: required fields, type checking,
     * string constraints (minLength, maxLength), numeric constraints (minimum, maximum).
     *
     * @param data   the data to validate
     * @param schema JSON Schema dictionary
     * @throws ValidationError if validation fails
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static void validateWithSchema(Map<String, Object> data,
                                          Map<String, Object> schema) {
        if (data == null) {
            throw new ValidationError(StatusCode.SCHEMA_VALIDATE_INVALID,
                    null, null, null,
                    Map.of("reason", "data is null", "data", "null"));
        }

        try {
            Map<String, Object> properties = getMapOrEmpty(schema, "properties");
            List<String> required = getListOrEmpty(schema, "required");
            Object additionalProperties = schema.get("additionalProperties");

            // Check for extra fields not in schema
            // Default to false when additionalProperties is not specified (matches Python behavior)
            boolean hasPropertyConstraint = !properties.isEmpty();
            boolean allowAdditional = !hasPropertyConstraint
                    || (additionalProperties instanceof Boolean
                    && ((Boolean) additionalProperties));
            if (!allowAdditional) {
                for (String field : data.keySet()) {
                    if (!properties.containsKey(field)) {
                        throw new IllegalArgumentException(
                                "Unexpected keyword argument: " + field);
                    }
                }
            }

            // Check required fields
            for (String field : required) {
                if (!data.containsKey(field)) {
                    throw new IllegalArgumentException(
                            "Missing required field: " + field);
                }
            }

            // Validate field constraints
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String fieldName = entry.getKey();
                if (!data.containsKey(fieldName)) {
                    continue;
                }

                Object value = data.get(fieldName);
                Map<String, Object> fieldSchema = (Map<String, Object>) entry.getValue();
                validateField(fieldName, value, fieldSchema);
            }
        } catch (ValidationError e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationError(StatusCode.SCHEMA_VALIDATE_INVALID,
                    null, null, e,
                    Map.of("reason", e.getMessage(), "data", String.valueOf(data)));
        }
    }

    /**
     * Get a schema dictionary representation from a Java class using Jackson.
     *
     * @param clazz the class to introspect
     * @return JSON Schema-like dictionary
     */
    public static Map<String, Object> getSchemaDict(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        try {
            JsonNode schemaNode = MAPPER.valueToTree(
                    MAPPER.getSerializationConfig()
                            .introspect(MAPPER.constructType(clazz)));
            // Build a simple schema from the class fields
            return buildSchemaFromClass(clazz);
        } catch (Exception e) {
            return buildSchemaFromClass(clazz);
        }
    }

    // ==================== Internal helpers ====================

    /**
     * Apply default values from schema to data for missing fields.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> applyDefaults(Map<String, Object> data,
                                                     Map<String, Object> schema) {
        Map<String, Object> result = new LinkedHashMap<>(data);
        Map<String, Object> properties = getMapOrEmpty(schema, "properties");

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> fieldSchema = (Map<String, Object>) entry.getValue();

            if (!result.containsKey(fieldName) && fieldSchema.containsKey("default")) {
                Object defaultValue = fieldSchema.get("default");
                // Deep copy mutable defaults
                if (defaultValue instanceof Map) {
                    result.put(fieldName, new LinkedHashMap<>((Map<?, ?>) defaultValue));
                } else if (defaultValue instanceof List) {
                    result.put(fieldName, new ArrayList<>((List<?>) defaultValue));
                } else {
                    result.put(fieldName, defaultValue);
                }
            }
        }

        return result;
    }

    /**
     * Validate a single field value against its schema constraints.
     */
    @SuppressWarnings("unchecked")
    private static void validateField(String fieldName, Object value,
                                      Map<String, Object> fieldSchema) {
        String type = (String) fieldSchema.get("type");
        if (type == null) {
            return;
        }

        // Type checking
        switch (type) {
            case "string" -> {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException("Input should be a valid string");
                }
                validateStringConstraints(fieldName, (String) value, fieldSchema);
            }
            case "integer" -> {
                if (!(value instanceof Number)) {
                    if (value instanceof String) {
                        throw new IllegalArgumentException(
                                "Input should be a valid integer, unable to parse string as an integer");
                    }
                    throw new IllegalArgumentException("Input should be a valid integer");
                }
                validateNumericConstraints(fieldName, ((Number) value).doubleValue(), fieldSchema);
            }
            case "number" -> {
                if (!(value instanceof Number)) {
                    if (value instanceof String) {
                        throw new IllegalArgumentException(
                                "Input should be a valid number, unable to parse string as a number");
                    }
                    throw new IllegalArgumentException("Input should be a valid number");
                }
                validateNumericConstraints(fieldName, ((Number) value).doubleValue(), fieldSchema);
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException("Input should be a valid boolean");
                }
            }
            case "array" -> {
                if (!(value instanceof List)) {
                    throw new IllegalArgumentException("Input should be a valid array");
                }
                validateArrayConstraints(fieldName, (List<?>) value, fieldSchema);
            }
            case "object" -> {
                if (!(value instanceof Map)) {
                    throw new IllegalArgumentException("Input should be a valid object");
                }
            }
            default -> { /* no-op for unknown types */ }
        }
    }

    private static void validateStringConstraints(String fieldName, String value,
                                                  Map<String, Object> fieldSchema) {
        Integer minLength = getIntOrNull(fieldSchema, "minLength");
        Integer maxLength = getIntOrNull(fieldSchema, "maxLength");

        if (minLength != null && value.length() < minLength) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' length " + value.length() +
                            " is less than minLength " + minLength);
        }
        if (maxLength != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' length " + value.length() +
                            " exceeds maxLength " + maxLength);
        }
    }

    private static void validateNumericConstraints(String fieldName, double value,
                                                   Map<String, Object> fieldSchema) {
        Number minimum = (Number) fieldSchema.get("minimum");
        Number maximum = (Number) fieldSchema.get("maximum");

        if (minimum != null && value < minimum.doubleValue()) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' value " + value +
                            " is less than minimum " + minimum);
        }
        if (maximum != null && value > maximum.doubleValue()) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' value " + value +
                            " exceeds maximum " + maximum);
        }
    }

    private static void validateArrayConstraints(String fieldName, List<?> value,
                                                 Map<String, Object> fieldSchema) {
        Integer minItems = getIntOrNull(fieldSchema, "minItems");
        Integer maxItems = getIntOrNull(fieldSchema, "maxItems");

        if (minItems != null && value.size() < minItems) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' size " + value.size() +
                            " is less than minItems " + minItems);
        }
        if (maxItems != null && value.size() > maxItems) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' size " + value.size() +
                            " exceeds maxItems " + maxItems);
        }
    }

    /**
     * Build a basic schema dictionary from class fields via reflection.
     */
    private static Map<String, Object> buildSchemaFromClass(Class<?> clazz) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("title", clazz.getSimpleName());

        Map<String, Object> properties = new LinkedHashMap<>();
        for (var field : clazz.getDeclaredFields()) {
            Map<String, Object> fieldSchema = new LinkedHashMap<>();
            Class<?> fieldType = field.getType();

            if (fieldType == String.class) {
                fieldSchema.put("type", "string");
            } else if (fieldType == int.class || fieldType == Integer.class
                    || fieldType == long.class || fieldType == Long.class) {
                fieldSchema.put("type", "integer");
            } else if (fieldType == double.class || fieldType == Double.class
                    || fieldType == float.class || fieldType == Float.class) {
                fieldSchema.put("type", "number");
            } else if (fieldType == boolean.class || fieldType == Boolean.class) {
                fieldSchema.put("type", "boolean");
            } else if (List.class.isAssignableFrom(fieldType)) {
                fieldSchema.put("type", "array");
            } else if (Map.class.isAssignableFrom(fieldType)) {
                fieldSchema.put("type", "object");
            } else {
                fieldSchema.put("type", "object");
            }

            properties.put(field.getName(), fieldSchema);
        }

        schema.put("properties", properties);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMapOrEmpty(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<String> getListOrEmpty(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return Collections.emptyList();
    }

    private static Integer getIntOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    // ==================== Null-value cleaning ====================

    /**
     * Recursively remove null values from a data structure.
     * <p>
     * Traverses through maps and lists, removing any null values while
     * preserving the structure for non-null values.
     *
     * @param data the input map to clean
     * @return a new map without null values, or null if all values were null
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> removeNoneValues(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object cleanedValue = removeNoneValue(entry.getValue());
            if (cleanedValue != null) {
                cleaned.put(entry.getKey(), cleanedValue);
            }
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Recursively clean a single value.
     */
    @SuppressWarnings("unchecked")
    private static Object removeNoneValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            Map<String, Object> mapValue = (Map<String, Object>) value;
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : mapValue.entrySet()) {
                Object cv = removeNoneValue(entry.getValue());
                if (cv != null) {
                    cleaned.put(entry.getKey(), cv);
                }
            }
            return cleaned.isEmpty() ? null : cleaned;
        }
        if (value instanceof List) {
            List<?> listValue = (List<?>) value;
            List<Object> cleaned = new ArrayList<>();
            for (Object item : listValue) {
                Object cv = removeNoneValue(item);
                if (cv != null) {
                    cleaned.add(cv);
                }
            }
            return cleaned.isEmpty() ? null : cleaned;
        }
        return value;
    }
}
