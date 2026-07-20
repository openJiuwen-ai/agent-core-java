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
 * <p>
 * {@code inputParams} and {@code outputParams} accept either a
 * {@code Map<String, Object>} (raw JSON-schema style) <b>or</b> a
 * {@code Class<?>} (model/schema type) to align with the Python
 * {@code dict[str, Any] | Type[BaseModel]} union.
 * </p>
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentCard extends BaseCard {
    private Object inputParams;

    /**
     * Output parameter schema — same typing rules as {@link #inputParams}.
     */
    private Object outputParams;

    /**
     * Resolve the given parameter holder to a {@code Map} suitable for
     * tool-info / JSON-schema contexts.
     * 
     * @param params params
     * @return the result
     * @since 0.1.7
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
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getInputParamsAsMap() {
        return resolveParams(inputParams);
    }

    /**
     * Get output params as a {@code Map}.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getOutputParamsAsMap() {
        return resolveParams(outputParams);
    }

    /**
     * toolInfo.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object toolInfo() {
        return ToolInfo.builder().name(getName()).description(getDescription()).parameters(getInputParamsAsMap())
                .build();
    }

    /**
     * getInputParams.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getInputParams() {
        return inputParams;
    }

    /**
     * setInputParams.
     * 
     * @param inputParams inputParams
     * @since 0.1.7
     */
    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }

    /**
     * getOutputParams.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getOutputParams() {
        return outputParams;
    }

    /**
     * setOutputParams.
     * 
     * @param outputParams outputParams
     * @since 0.1.7
     */
    public void setOutputParams(Object outputParams) {
        this.outputParams = outputParams;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder extends BaseCard.Builder {
        private Object inputParams;
        private Object outputParams;

        /**
         * id.
         * 
         * @param id id
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * description.
         * 
         * @param description description
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        /**
         * inputParams.
         * 
         * @param inputParams inputParams
         * @return the result
         * @since 0.1.7
         */
        public Builder inputParams(Object inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        /**
         * outputParams.
         * 
         * @param outputParams outputParams
         * @return the result
         * @since 0.1.7
         */
        public Builder outputParams(Object outputParams) {
            this.outputParams = outputParams;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public AgentCard build() {
            AgentCard card = new AgentCard();
            // Keep BaseCard's default UUID when caller did not set id explicitly.
            if (id != null && !id.isBlank()) {
                card.setId(id);
            }
            card.setName(name);
            card.setDescription(description);
            card.setInputParams(inputParams);
            card.setOutputParams(outputParams);
            return card;
        }
    }
}
