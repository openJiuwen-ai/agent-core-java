/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Metadata card for a workflow.
 * Contains descriptive information and input schema for a workflow.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.base.WorkflowCard}.
 */
@Data
@SuperBuilder
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
        setId(id);
        setName(name);
    }

    public WorkflowCard() {
        super();
    }

    @Builder.Default
    private String version = "";

    private Object inputParams;

    public static WorkflowCardBuilder builder() {
        return new WorkflowCardBuilder();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Object getInputParams() {
        return inputParams;
    }

    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }

    public static final class WorkflowCardBuilder {
        private String id = "";
        private String name = "";
        private String description = "";
        private String version = "";
        private Object inputParams;

        public WorkflowCardBuilder id(String id) { this.id = id; return this; }
        public WorkflowCardBuilder name(String name) { this.name = name; return this; }
        public WorkflowCardBuilder description(String description) { this.description = description; return this; }
        public WorkflowCardBuilder version(String version) { this.version = version; return this; }
        public WorkflowCardBuilder inputParams(Object inputParams) { this.inputParams = inputParams; return this; }

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

    /**
     * Compatibility constructor for translated tests that still pass
     * {@code id, name, version, description} positionally.
     */
    public WorkflowCard(String id, String name, String version, String description) {
        setId(id);
        setName(name);
        setVersion(version);
        setDescription(description);
    }

    @Override
    public Object toolInfo() {
        return ToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(resolveInputParamsSchema())
                .build();
    }

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
}
