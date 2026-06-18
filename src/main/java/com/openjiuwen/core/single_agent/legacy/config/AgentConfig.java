/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy agent base configuration.
 *
 * <p>Mirrors Python's {@code AgentConfig} in
 * {@code openjiuwen/core/single_agent/legacy/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentConfig {
    private String id = "";
    private String version = "";
    private String description = "";

    @JsonProperty("controller_type")
    private ControllerType controllerType = ControllerType.UNDEFINED;

    private List<Object> workflows = new ArrayList<>();
    private ModelConfig model;
    private List<String> tools = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null ? "" : version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public ControllerType getControllerType() {
        return controllerType;
    }

    public void setControllerType(ControllerType controllerType) {
        this.controllerType = controllerType == null ? ControllerType.UNDEFINED : controllerType;
    }

    public List<Object> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(List<?> workflows) {
        this.workflows = workflows == null ? new ArrayList<>() : new ArrayList<>(workflows);
    }

    public ModelConfig getModel() {
        return model;
    }

    public void setModel(ModelConfig model) {
        this.model = model;
    }

    public List<String> getTools() {
        return tools;
    }

    public void setTools(List<String> tools) {
        this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
    }
}
