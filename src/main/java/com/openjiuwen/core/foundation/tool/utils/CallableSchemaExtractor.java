/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reflection-based extractor that turns Java method signatures into JSON Schema.
 * 
 * @since 0.1.7
 */
public final class CallableSchemaExtractor {
    /**
     * CallableSchemaExtractor.
     * 
     * @since 0.1.7
     */
    private CallableSchemaExtractor() {
    }

    /**
     * generateSchema.
     * 
     * @param method method
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> generateSchema(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter parameter : method.getParameters()) {
            Map<String, Object> parameterSchema = TypeSchemaExtractor.extract(parameter.getParameterizedType());
            parameterSchema.putIfAbsent("description", humanizeName(parameter.getName()));
            properties.put(parameter.getName(), parameterSchema);
            if (!Optional.class.isAssignableFrom(parameter.getType())) {
                required.add(parameter.getName());
            }
        }

        schema.put("type", "object");
        schema.put("title", humanizeName(method.getName()));
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /**
     * extractFunctionDescription.
     * 
     * @param method method
     * @return the result
     * @since 0.1.7
     */
    public static String extractFunctionDescription(Method method) {
        return humanizeName(method.getName());
    }

    static String humanizeName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String snakeNormalized = name.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return snakeNormalized.trim().toLowerCase(Locale.ROOT);
    }
}
