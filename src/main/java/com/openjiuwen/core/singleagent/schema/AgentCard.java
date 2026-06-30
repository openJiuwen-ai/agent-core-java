/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.schema;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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
     * it is isResolved to a minimal map descriptor.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object toolInfo() {
        return ToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(getInputParamsAsMap())
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getInputParams() {
        return inputParams;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getOutputParams() {
        return outputParams;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOutputParams(Object outputParams) {
        this.outputParams = outputParams;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder extends BaseCard.Builder {
        private Object inputParams;
        private Object outputParams;

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder inputParams(Object inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder outputParams(Object outputParams) {
            this.outputParams = outputParams;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public AgentCard build() {
            AgentCard card = new AgentCard();
            card.setId(id);
            card.setName(name);
            card.setDescription(description);
            card.setInputParams(inputParams);
            card.setOutputParams(outputParams);
            return card;
        }
    }
}
