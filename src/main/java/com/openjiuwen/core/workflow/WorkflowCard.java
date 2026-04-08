/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

    @Builder.Default
    private String version = "";

    private Object inputParams;

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
