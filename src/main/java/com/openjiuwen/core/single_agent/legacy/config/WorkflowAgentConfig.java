/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy workflow-agent configuration.
 *
 * <p>Mirrors Python's {@code WorkflowAgentConfig} in
 * {@code openjiuwen/core/single_agent/legacy/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowAgentConfig extends AgentConfig {
    @JsonProperty("start_workflow")
    private WorkflowSchema startWorkflow = new WorkflowSchema();

    @JsonProperty("end_workflow")
    private WorkflowSchema endWorkflow = new WorkflowSchema();

    @JsonProperty("global_variables")
    private List<Map<String, Object>> globalVariables = new ArrayList<>();

    @JsonProperty("global_params")
    private Map<String, Object> globalParams = new LinkedHashMap<>();

    private ConstrainConfig constrain = new ConstrainConfig();

    @JsonProperty("default_response")
    private ControllerConfig.DefaultResponse defaultResponse = new ControllerConfig.DefaultResponse();

    public WorkflowAgentConfig() {
        setControllerType(ControllerType.WORKFLOW_CONTROLLER);
    }

    public WorkflowSchema getStartWorkflow() {
        return startWorkflow;
    }

    public void setStartWorkflow(WorkflowSchema startWorkflow) {
        this.startWorkflow = startWorkflow == null ? new WorkflowSchema() : startWorkflow;
    }

    public WorkflowSchema getEndWorkflow() {
        return endWorkflow;
    }

    public void setEndWorkflow(WorkflowSchema endWorkflow) {
        this.endWorkflow = endWorkflow == null ? new WorkflowSchema() : endWorkflow;
    }

    public List<Map<String, Object>> getGlobalVariables() {
        return globalVariables;
    }

    public void setGlobalVariables(List<Map<String, Object>> globalVariables) {
        this.globalVariables = copyList(globalVariables);
    }

    public Map<String, Object> getGlobalParams() {
        return globalParams;
    }

    public void setGlobalParams(Map<String, Object> globalParams) {
        this.globalParams = globalParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(globalParams);
    }

    public ConstrainConfig getConstrain() {
        return constrain;
    }

    public void setConstrain(ConstrainConfig constrain) {
        this.constrain = constrain == null ? new ConstrainConfig() : constrain;
    }

    public ControllerConfig.DefaultResponse getDefaultResponse() {
        return defaultResponse;
    }

    public void setDefaultResponse(ControllerConfig.DefaultResponse defaultResponse) {
        this.defaultResponse = defaultResponse == null ? new ControllerConfig.DefaultResponse() : defaultResponse;
    }

    private static List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, Object> item : source) {
                copy.add(item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item));
            }
        }
        return copy;
    }
}
