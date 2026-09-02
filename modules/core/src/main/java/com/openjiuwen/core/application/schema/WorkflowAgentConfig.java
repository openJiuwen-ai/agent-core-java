/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;

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
 * 
 * @since 0.1.7
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

    private ModelConfig model;

    @Builder.Default
    @JsonProperty("controller_type")
    @JsonAlias("controllerType")
    private ControllerType controllerType = ControllerType.WORKFLOW_CONTROLLER;

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<WorkflowSchema> workflows = new ArrayList<>();

    @Builder.Default
    @JsonProperty("prompt_template")
    @JsonAlias("promptTemplate")
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, String>> promptTemplate = new ArrayList<>();

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> tools = new ArrayList<>();

    @Builder.Default
    @JsonProperty("start_workflow")
    @JsonAlias("startWorkflow")
    /**
     * WorkflowSchema.builder.
     * 
     * @since 0.1.7
     */
    private WorkflowSchema startWorkflow = WorkflowSchema.builder().build();

    @Builder.Default
    @JsonProperty("end_workflow")
    @JsonAlias("endWorkflow")
    /**
     * WorkflowSchema.builder.
     * 
     * @since 0.1.7
     */
    private WorkflowSchema endWorkflow = WorkflowSchema.builder().build();

    @Builder.Default
    @JsonProperty("global_variables")
    @JsonAlias("globalVariables")
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, Object>> globalVariables = new ArrayList<>();

    @Builder.Default
    @JsonProperty("global_params")
    @JsonAlias("globalParams")
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
    @JsonProperty("default_response")
    @JsonAlias("defaultResponse")
    /**
     * DefaultResponse.builder.
     * 
     * @since 0.1.7
     */
    private DefaultResponse defaultResponse = DefaultResponse.builder().build();

    @JsonProperty("context_engine_config")
    @JsonAlias("contextEngineConfig")
    private ContextEngineConfig contextEngineConfig;
}
