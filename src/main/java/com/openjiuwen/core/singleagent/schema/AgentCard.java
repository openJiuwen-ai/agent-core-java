/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.schema;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent card data class.
 * Mirrors Python's {@code AgentCard} in {@code single_agent/schema/agent_card.py}.
 *
 * <p>{@code inputParams} and {@code outputParams} accept either a
 * {@code Map<String, Object>} (raw JSON-schema style) <b>or</b> a
 * {@code Class<?>} (model/schema type) to align with the Python
 * {@code dict[str, Any] | Type[BaseModel]} union.</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentCard extends BaseCard {

    /**
     * Optional version tag for compatibility with team tooling.
     */
    @Builder.Default
    private String version = "";

    /**
     * Input parameter schema — may be a {@code Map<String, Object>} <b>or</b>
     * a {@code Class<?>} representing a model type.
     */
    private Object inputParams;

    /**
     * Output parameter schema — same typing rules as {@link #inputParams}.
     */
    private Object outputParams;

    /**
     * Resolve the given parameter holder to a {@code Map} suitable for
     * tool-info / JSON-schema contexts.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveParams(Object params) {
        if (params == null) {
            return emptyObjectSchema();
        }
        if (params instanceof Map) {
            return (Map<String, Object>) params;
        }
        if (params instanceof Class<?> cls) {
            return classToSchema(cls);
        }
        return emptyObjectSchema();
    }

    private static Map<String, Object> emptyObjectSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        );
    }

    private static Map<String, Object> classToSchema(Class<?> cls) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            properties.put(field.getName(), Map.of("type", jsonType(field.getType())));
        }
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of()
        );
    }

    private static String jsonType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (Number.class.isAssignableFrom(type)
                || type == byte.class || type == short.class || type == int.class || type == long.class
                || type == float.class || type == double.class) {
            return "number";
        }
        if (List.class.isAssignableFrom(type)) {
            return "array";
        }
        if (Map.class.isAssignableFrom(type)) {
            return "object";
        }
        return "string";
    }

    /**
     * Get input params as a {@code Map}. If a {@code Class<?>} was stored,
     * it is resolved to a minimal map descriptor.
     */
    public Map<String, Object> getInputParamsAsMap() {
        return resolveParams(inputParams);
    }

    /**
     * Get output params as a {@code Map}.
     */
    public Map<String, Object> getOutputParamsAsMap() {
        return resolveParams(outputParams);
    }

    @Override
    public Object toolInfo() {
        return ToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(getInputParamsAsMap())
                .build();
    }
}
