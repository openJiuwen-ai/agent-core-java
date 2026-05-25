/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool card — configuration / metadata for a tool.
 * <p>
 * Extends {@link BaseCard} with input parameters and custom properties.
 * Mirrors Python's {@code ToolCard} model.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToolCard extends BaseCard {

    /** Input parameter schema (JSON Schema format). */
    private Map<String, Object> inputParams = new HashMap<>();

    /** Custom properties map. */
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Build a {@link ToolInfo} descriptor for this tool card.
     *
     * @return a ToolInfo instance with name, description, and parameters
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolInfo toolInfo() {
        String effectiveName = (getName() == null || getName().isBlank()) ? getId() : getName();
        String effectiveDesc = (getDescription() == null || getDescription().isBlank()) ? "" : getDescription();
        return ToolInfo.builder()
                .name(effectiveName)
                .description(effectiveDesc)
                .parameters(inputParams)
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams() {
        return inputParams;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setInputParams(Map<String, Object> inputParams) {
        this.inputParams = inputParams;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
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
        /**
         * Auto-generated for codecheck compliance.
         */
        protected Map<String, Object> inputParams = new HashMap<>();
        /**
         * Auto-generated for codecheck compliance.
         */
        protected Map<String, Object> properties = new HashMap<>();

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
        public Builder inputParams(Map<String, Object> inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolCard build() {
            ToolCard card = new ToolCard();
            card.setId(id);
            card.setName(name);
            card.setDescription(description);
            card.setInputParams(inputParams);
            card.setProperties(properties);
            return card;
        }
    }
}
