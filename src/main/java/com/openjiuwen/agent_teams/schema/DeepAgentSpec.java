/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.harness.DeepAgentConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal serializable wrapper around a Java DeepAgentConfig.
 *
 * <p>Mirrors Python's {@code DeepAgentSpec} in
 * {@code openjiuwen.agent_teams.schema.deep_agent_spec} and re-export usage in
 * {@code openjiuwen.agent_teams.schema.blueprint}.
 */
public class DeepAgentSpec {

    private DeepAgentConfig config;
    private TeamModelConfig model;
    private String language;
    private List<String> approvalRequiredTools = new ArrayList<>();

    public DeepAgentConfig getConfig() {
        return config;
    }

    public void setConfig(DeepAgentConfig config) {
        this.config = config;
    }

    public TeamModelConfig getModel() {
        return model;
    }

    public void setModel(TeamModelConfig model) {
        this.model = model;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<String> getApprovalRequiredTools() {
        return new ArrayList<>(approvalRequiredTools);
    }

    public void setApprovalRequiredTools(List<String> approvalRequiredTools) {
        this.approvalRequiredTools = approvalRequiredTools != null
                ? new ArrayList<>(approvalRequiredTools) : new ArrayList<>();
    }
}
