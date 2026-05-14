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
    private List<WorkflowSchema> workflows = new ArrayList<>();

    @Builder.Default
    @JsonProperty("prompt_template")
    @JsonAlias("promptTemplate")
    private List<Map<String, String>> promptTemplate = new ArrayList<>();

    @Builder.Default
    private List<String> tools = new ArrayList<>();

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

    public List<WorkflowSchema> getWorkflows() { return workflows; }
    public void setWorkflows(List<WorkflowSchema> workflows) { this.workflows = workflows; }
    public List<Map<String, String>> getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(List<Map<String, String>> promptTemplate) { this.promptTemplate = promptTemplate; }
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
    public WorkflowSchema getStartWorkflow() { return startWorkflow; }
    public void setStartWorkflow(WorkflowSchema startWorkflow) { this.startWorkflow = startWorkflow; }
    public WorkflowSchema getEndWorkflow() { return endWorkflow; }
    public void setEndWorkflow(WorkflowSchema endWorkflow) { this.endWorkflow = endWorkflow; }
    public List<Map<String, Object>> getGlobalVariables() { return globalVariables; }
    public void setGlobalVariables(List<Map<String, Object>> globalVariables) { this.globalVariables = globalVariables; }
    public Map<String, Object> getGlobalParams() { return globalParams; }
    public void setGlobalParams(Map<String, Object> globalParams) { this.globalParams = globalParams; }
    public ConstrainConfig getConstrain() { return constrain; }
    public void setConstrain(ConstrainConfig constrain) { this.constrain = constrain; }
    public DefaultResponse getDefaultResponse() { return defaultResponse; }
    public void setDefaultResponse(DefaultResponse defaultResponse) { this.defaultResponse = defaultResponse; }
    public ContextEngineConfig getContextEngineConfig() { return contextEngineConfig; }
    public void setContextEngineConfig(ContextEngineConfig contextEngineConfig) { this.contextEngineConfig = contextEngineConfig; }

    @JsonProperty("context_engine_config")
    @JsonAlias("contextEngineConfig")
    private ContextEngineConfig contextEngineConfig;
}
