/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.singleagent.schema;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
            return Map.of();
        }
        if (params instanceof Map) {
            return (Map<String, Object>) params;
        }
        if (params instanceof Class<?> cls) {
            // Return a minimal descriptor so callers can identify the schema type.
            return Map.of("$javaClass", cls.getName());
        }
        return Map.of();
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
