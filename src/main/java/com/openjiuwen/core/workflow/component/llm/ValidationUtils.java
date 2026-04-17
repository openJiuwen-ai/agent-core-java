/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.List;
import java.util.Map;

/**
 * Validation utilities for LLM component inputs and outputs.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.ValidationUtils}.
 */
public final class ValidationUtils {

    private ValidationUtils() {
    }

    /**
     * Raise an invalid params error.
     */
    public static void raiseInvalidParamsError(String errorMsg) {
        throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_CONFIG_INVALID, "error_msg", errorMsg);
    }

    /**
     * Validate that the instance matches the expected type.
     */
    public static void validateType(Object instance, String expectedType) {
        boolean valid = switch (expectedType) {
            case "object" -> instance instanceof Map;
            case "array" -> instance instanceof List;
            case "string" -> instance instanceof String;
            case "integer" -> instance instanceof Integer || instance instanceof Long;
            case "boolean" -> instance instanceof Boolean;
            case "number" -> instance instanceof Number && !(instance instanceof Boolean);
            default -> false;
        };

        if (!valid) {
            raiseInvalidParamsError(expectedType + " is not the type of " + (instance == null ? "null" : instance.getClass().getSimpleName()));
        }
    }

    /**
     * Validate an instance against a JSON schema (simplified).
     */
    public static void validateJsonSchema(Object instance, Map<String, Object> schema) {
        String type = (String) schema.get("type");
        if (type == null) {
            raiseInvalidParamsError("schema must have 'type' key");
        }

        validateType(instance, type);

        if ("object".equals(type)) {
            validateObjectProperties(instance, schema);
        } else if ("array".equals(type)) {
            validateArrayItems(instance, schema);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateObjectProperties(Object instance, Map<String, Object> schema) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            return;
        }
        Map<String, Object> instanceMap = (Map<String, Object>) instance;
        List<String> requiredFields = (List<String>) schema.getOrDefault("required", List.of());

        for (String field : requiredFields) {
            if (!instanceMap.containsKey(field)) {
                raiseInvalidParamsError("missing required property: " + field);
            }
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propName = entry.getKey();
            if (instanceMap.containsKey(propName)) {
                Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();
                validateJsonSchema(instanceMap.get(propName), propSchema);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateArrayItems(Object instance, Map<String, Object> schema) {
        Map<String, Object> items = (Map<String, Object>) schema.get("items");
        if (items == null) {
            return;
        }
        List<?> list = (List<?>) instance;
        for (int i = 0; i < list.size(); i++) {
            try {
                validateJsonSchema(list.get(i), items);
            } catch (Exception e) {
                raiseInvalidParamsError("invalid array item " + i + ": " + e.getMessage());
            }
        }
    }

    /**
     * Validate that output config is non-empty and is a Map.
     */
    public static void validateOutputsConfig(Object outputsConfig) {
        if (outputsConfig == null) {
            raiseInvalidParamsError("outputs config must not be empty");
        }
        if (!(outputsConfig instanceof Map)) {
            raiseInvalidParamsError("outputs config must be a dict");
        }
    }
}
