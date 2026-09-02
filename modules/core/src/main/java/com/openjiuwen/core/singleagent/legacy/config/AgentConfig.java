/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.openjiuwen.core.workflow.WorkflowCard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy agent configuration.
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {
    @Builder.Default
    private String id = "";

    @Builder.Default
    private String version = "";

    @Builder.Default
    private String description = "";

    @Builder.Default
    private ControllerType controllerType = ControllerType.UNDEFINED;

    /**
     * Workflow list – accepts both {@link WorkflowSchema} and {@link WorkflowCard}
     * to mirror Python's {@code List[Union[WorkflowSchema, WorkflowCard]]}.
     * 
     * @since 0.1.7
     */
    @Builder.Default
    private List<Object> workflows = new ArrayList<>();

    private ModelConfig model;

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> tools = new ArrayList<>();
}
