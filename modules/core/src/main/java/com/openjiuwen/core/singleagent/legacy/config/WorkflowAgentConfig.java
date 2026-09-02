/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy workflow-agent configuration.
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkflowAgentConfig extends AgentConfig {
    @Builder.Default
    private ControllerType controllerType = ControllerType.WORKFLOW_CONTROLLER;

    @Builder.Default
    /**
     * WorkflowSchema.
     * 
     * @since 0.1.7
     */
    private WorkflowSchema startWorkflow = new WorkflowSchema();

    @Builder.Default
    /**
     * WorkflowSchema.
     * 
     * @since 0.1.7
     */
    private WorkflowSchema endWorkflow = new WorkflowSchema();

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, Object>> globalVariables = new ArrayList<>();

    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> globalParams = new LinkedHashMap<>();

    @Builder.Default
    /**
     * ConstrainConfig.builder.
     * 
     * @since 0.1.7
     */
    private ConstrainConfig constrain = ConstrainConfig.builder().build();

    @Builder.Default
    /**
     * DefaultResponse.builder.
     * 
     * @since 0.1.7
     */
    private DefaultResponse defaultResponse = DefaultResponse.builder().build();
}
