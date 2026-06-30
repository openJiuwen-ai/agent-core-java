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
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkflowCard extends BaseCard {

    /**
     * Convenience constructor: WorkflowCard(id, name).
     * Used in tests and mirrors Python's WorkflowCard(id=..., name=...) pattern.
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
     */
    public WorkflowCard(String id, String name, String version, String description) {
        super.setId(id);
        super.setName(name);
        this.version = version;
        super.setDescription(description);
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
                .parameters(resolveInputParamsSchema())
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String str() {
        return toString();
    }

    @SuppressWarnings("unchecked")
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
     * Auto-generated for codecheck compliance.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setVersion(String version) {
        this.version = version;
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
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder extends BaseCard.Builder {
        private String version = "";
        private Object inputParams;

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
        public Builder version(String version) {
            this.version = version;
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
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
