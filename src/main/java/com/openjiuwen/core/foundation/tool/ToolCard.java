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
     * Raw inputParams object — can be a Map or a model Class.
     * <p>
     * Mirrors Python's {@code input_params: Dict[str, Any] | Type[BaseModel]}.
     * When set to a non-Map value (e.g., a Class), {@link #getInputParams()} returns
     * the default empty Map, while {@link #getInputParamsRaw()} returns the original object.
     *
     * @since 0.1.14
     */
    private Object inputParamsRaw;

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
        this.inputParamsRaw = inputParams;
    }

    /**
     * getInputParamsRaw.
     * <p>
     * Returns the raw inputParams object, which may be a Map or a model Class.
     *
     * @return the raw inputParams object, or null if not set
     * @since 0.1.14
     */
    public Object getInputParamsRaw() {
        return inputParamsRaw;
    }

    /**
     * setInputParamsRaw.
     * <p>
     * Sets the raw inputParams object. When the value is a Map, it also updates
     * the typed {@code inputParams} field for backward compatibility.
     *
     * @param inputParamsRaw the raw inputParams object (Map or model Class)
     * @since 0.1.14
     */
    public void setInputParamsRaw(Object inputParamsRaw) {
        this.inputParamsRaw = inputParamsRaw;
        if (inputParamsRaw instanceof Map) {
            this.inputParams = (Map<String, Object>) inputParamsRaw;
        }
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
         * Raw inputParams for model class support.
         *
         * @since 0.1.14
         */
        protected Object inputParamsRaw;

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
            this.inputParamsRaw = inputParams;
            return this;
        }

        /**
         * inputParams accepting any Object (Map or model Class).
         * <p>
         * Mirrors Python's {@code input_params: Dict[str, Any] | Type[BaseModel]}.
         * When a non-Map value is passed, it is stored in {@code inputParamsRaw}
         * for retrieval via {@link ToolCard#getInputParamsRaw()}.
         *
         * @param inputParams the input parameters (Map or model Class)
         * @return this builder
         * @since 0.1.14
         */
        public Builder inputParams(Object inputParams) {
            this.inputParamsRaw = inputParams;
            if (inputParams instanceof Map) {
                this.inputParams = (Map<String, Object>) inputParams;
            }
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
            if (inputParamsRaw != null) {
                card.setInputParamsRaw(inputParamsRaw);
            }
            card.setProperties(properties);
            return card;
        }
    }
}
