/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.context.schema.ContextEngineConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for workflow-based agent in the application layer.
 * <p>
 * Mirrors Python's {@code WorkflowAgentConfig} used by WorkflowAgent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAgentConfig {

    private String id;

    @Builder.Default
    private String version = "1.0";

    @Builder.Default
    private String description = "";

    @Builder.Default
    @JsonProperty("controller_type")
    @JsonAlias("controllerType")
    private ControllerType controllerType = ControllerType.WORKFLOW_CONTROLLER;

    @Builder.Default
    private List<WorkflowSchema> workflows = new ArrayList<>();

    @Builder.Default
    @JsonProperty("start_workflow")
    @JsonAlias("startWorkflow")
    private WorkflowSchema startWorkflow = WorkflowSchema.builder().build();

    @Builder.Default
    @JsonProperty("end_workflow")
    @JsonAlias("endWorkflow")
    private WorkflowSchema endWorkflow = WorkflowSchema.builder().build();

    @Builder.Default
    @JsonProperty("global_variables")
    @JsonAlias("globalVariables")
    private List<Map<String, Object>> globalVariables = new ArrayList<>();

    @Builder.Default
    @JsonProperty("global_params")
    @JsonAlias("globalParams")
    private Map<String, Object> globalParams = new LinkedHashMap<>();

    @Builder.Default
    private ConstrainConfig constrain = ConstrainConfig.builder().build();

    @Builder.Default
    @JsonProperty("default_response")
    @JsonAlias("defaultResponse")
    private DefaultResponse defaultResponse = DefaultResponse.builder().build();

    @JsonProperty("context_engine_config")
    @JsonAlias("contextEngineConfig")
    private ContextEngineConfig contextEngineConfig;
}
