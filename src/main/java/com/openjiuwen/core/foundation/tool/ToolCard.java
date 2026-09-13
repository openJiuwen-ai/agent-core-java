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
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToolCard extends BaseCard {
    private Map<String, Object> inputParams = new HashMap<>();

    /**
     * Custom properties map.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> properties = new HashMap<>();

    /**
     * toolInfo.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ToolInfo toolInfo() {
        String effectiveName = (getName() == null || getName().isBlank()) ? getId() : getName();
        String effectiveDesc = (getDescription() == null || getDescription().isBlank()) ? "" : getDescription();
        return ToolInfo.builder().name(effectiveName).description(effectiveDesc).parameters(inputParams).build();
    }

    /**
     * getInputParams.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getInputParams() {
        return inputParams;
    }

    /**
     * setInputParams.
     * 
     * @param inputParams inputParams
     * @since 0.1.7
     */
    public void setInputParams(Map<String, Object> inputParams) {
        this.inputParams = inputParams;
    }

    /**
     * getProperties.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * setProperties.
     * 
     * @param properties properties
     * @since 0.1.7
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
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
        /**
         * inputParams.
         * 
         * @since 0.1.7
         */
        protected Map<String, Object> inputParams = new HashMap<>();

        /**
         * properties.
         * 
         * @since 0.1.7
         */
        protected Map<String, Object> properties = new HashMap<>();

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
        public Builder inputParams(Map<String, Object> inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        /**
         * properties.
         * 
         * @param properties properties
         * @return the result
         * @since 0.1.7
         */
        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public ToolCard build() {
            ToolCard card = new ToolCard();
            if (id != null) {
                card.setId(id);
            }
            card.setName(name);
            card.setDescription(description);
            card.setInputParams(inputParams);
            card.setProperties(properties);
            return card;
        }
    }
}
