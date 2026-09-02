/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Metadata card for a workflow.
 * Contains descriptive information and input schema for a workflow.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.base.WorkflowCard}.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkflowCard extends BaseCard {
    /**
     * WorkflowCard.
     * 
     * @param id id
     * @param name name
     * @since 0.1.7
     */
    public WorkflowCard(String id, String name) {
        super();
        super.setId(id);
        super.setName(name);
    }

    private String version = "";

    private Object inputParams;

    /**
     * Compatibility constructor for translated tests that still pass
     * {@code id, name, version, description} positionally.
     * 
     * @param id id
     * @param name name
     * @param version version
     * @param description description
     * @since 0.1.7
     */
    public WorkflowCard(String id, String name, String version, String description) {
        super.setId(id);
        super.setName(name);
        this.version = version;
        super.setDescription(description);
    }

    /**
     * toolInfo.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object toolInfo() {
        return ToolInfo.builder().name(getName()).description(getDescription()).parameters(resolveInputParamsSchema())
                .build();
    }

    /**
     * str.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String str() {
        return toString();
    }

    @SuppressWarnings("unchecked")
    /**
     * resolveInputParamsSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> resolveInputParamsSchema() {
        if (inputParams == null) {
            return Map.of();
        }
        if (inputParams instanceof Map<?, ?> params) {
            return (Map<String, Object>) params;
        }
        if (inputParams instanceof Class<?> clazz) {
            return SchemaUtils.getSchemaDict(clazz);
        }
        return Map.of();
    }

    /**
     * getVersion.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getVersion() {
        return version;
    }

    /**
     * setVersion.
     * 
     * @param version version
     * @since 0.1.7
     */
    public void setVersion(String version) {
        this.version = version;
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
        private String version = "";
        private Object inputParams;

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
         * version.
         * 
         * @param version version
         * @return the result
         * @since 0.1.7
         */
        public Builder version(String version) {
            this.version = version;
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
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public WorkflowCard build() {
            WorkflowCard card = new WorkflowCard();
            card.setId(id);
            card.setName(name);
            card.setDescription(description);
            card.setVersion(version);
            card.setInputParams(inputParams);
            return card;
        }
    }
}
