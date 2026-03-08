/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.schema.BaseCard;
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

    @Builder.Default
    private String version = "";

    private Map<String, Object> inputParams;

    @Override
    public Object toolInfo() {
        return ToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(inputParams != null ? inputParams : Map.of())
                .build();
    }

    public String str() {
        return toString();
    }
}
